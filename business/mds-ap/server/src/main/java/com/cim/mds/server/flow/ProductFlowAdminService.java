package com.cim.mds.server.flow;

import java.time.LocalDateTime;
import java.util.List;

import com.cim.mds.server.site.CimProduct;
import com.cim.mds.server.site.CimProductRepository;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 产品↔流程绑定服务（P1：绑定/解绑/换版；先用「绑不同流程版本」表达产品差异）。
 *
 * <p>守门：产品须存在（NPW 用途产品拒绑）；同 (product, family) 最多一条 ACTIVE（换绑 = 旧 ACTIVE →
 * HISTORY 留痕）；绑定版本须 ACTIVE 或 FROZEN+VALID（预绑定，投批解析到 ACTIVE）；
 * 编辑锁人 vs 人互斥（同 bay/bank 模式）。flow disable 前的绑定守门在
 * {@link FlowAdminService#disable} 调 {@link CimProductFlowRepository#existsByFlowFamilyCodeAndBindStateAndDeletedFalse}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductFlowAdminService {

    private final CimProductFlowRepository bindingRepository;
    private final CimProductRepository productRepository;
    private final CimFlowRepository flowRepository;
    private final FlowNotifyService flowNotifyService;
    private final com.cim.mds.server.reticle.CimReticleRepository reticleRepository;

    /** 绑定请求（POST = 绑定/换版激活）。 */
    public record BindReq(String productCode, String flowFamilyCode, Integer flowVersionNo) {
    }

    /** 绑定视图（reticleRef/contam/npwLimit = V25 L3 列型覆盖，NULL = 继承）。 */
    public record BindingView(String id, String productCode, String flowFamilyCode,
                              Integer flowVersionNo, String bindState,
                              String activeTime, String activeUser,
                              Boolean isOccupied, String occupiedBy,
                              String reticleRef, String contamLevel,
                              String contamForbidden, Integer npwLimit) {
    }

    /** 列型覆盖更新请求（V25 L3；全部可空 = 清除覆盖回落继承）。 */
    public record OverrideReq(String reticleRef, String contamLevel,
                              String contamForbidden, Integer npwLimit) {
    }

    @Transactional(readOnly = true)
    public List<BindingView> listByProduct(String productCode) {
        return bindingRepository
                .findByProductCodeAndDeletedFalseOrderByActiveTimeDesc(productCode).stream()
                .map(ProductFlowAdminService::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<BindingView> listAll() {
        return bindingRepository.findAll().stream()
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .map(ProductFlowAdminService::toView).toList();
    }

    /** 绑定/换版：同族旧 ACTIVE → HISTORY，新行 ACTIVE（复用 uk：同 (product,family,version) 若已有 HISTORY 行则复活该行）。 */
    @Transactional
    public BindingView bind(BindReq req) {
        String product = req.productCode() == null ? "" : req.productCode().trim();
        String family = req.flowFamilyCode() == null ? "" : req.flowFamilyCode().trim();
        Integer version = req.flowVersionNo();
        if (product.isBlank() || family.isBlank() || version == null || version < 1) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.bind.paramRequired");
        }
        // 产品存在性 + NPW 守门（2026-11-25 定稿）：非产品陪跑片不挂产品流程
        // （虚拟产品口径；若将来开放 NPW 专用简单流程须另行建模绑定通道）
        CimProduct productEntity = productRepository.findByProductCodeAndDeletedFalse(product)
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.bind.productNotFound", product));
        if ("NPW".equals(productEntity.getUsageType())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.bind.npwNotBindable", product);
        }
        // 绑定版本守门：ACTIVE 或 FROZEN+VALID（预绑定）
        CimFlow target = flowRepository
                .findByFamilyCodeAndDeletedFalse(family).stream()
                .filter(f -> version.equals(f.getVersionNo()))
                .findFirst()
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.bind.flowVersionNotFound", family, version));
        boolean preBind = CimFlow.STATUS_FROZEN.equals(target.getStatus())
                && CimFlow.VS_VALID.equals(target.getValidateState());
        if (!CimFlow.STATUS_ACTIVE.equals(target.getStatus()) && !preBind) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.bind.versionNotBindable",
                    target.getStatus(), target.getValidateState());
        }
        // 同族旧 ACTIVE → HISTORY（留痕）
        bindingRepository.findByProductCodeAndFlowFamilyCodeAndBindStateAndDeletedFalse(
                product, family, CimProductFlow.BIND_ACTIVE)
                .filter(old -> !old.getFlowVersionNo().equals(version))
                .ifPresent(old -> {
                    old.setBindState(CimProductFlow.BIND_HISTORY);
                    bindingRepository.save(old);
                });
        // 同 (product, family, version) 已有行 → 复活为 ACTIVE（否则新建）
        CimProductFlow row = bindingRepository
                .findByProductCodeAndFlowFamilyCodeAndDeletedFalseOrderByActiveTimeDesc(product, family)
                .stream().filter(b -> version.equals(b.getFlowVersionNo()))
                .findFirst()
                .orElseGet(() -> {
                    CimProductFlow n = new CimProductFlow();
                    n.setProductCode(product);
                    n.setFlowFamilyCode(family);
                    n.setFlowVersionNo(version);
                    return n;
                });
        row.setBindState(CimProductFlow.BIND_ACTIVE);
        row.setActiveTime(LocalDateTime.now());
        row.setActiveUser(EditLockSupport.currentUser());
        CimProductFlow saved = bindingRepository.save(row);
        // P3-3：绑定变更 → 通知目标版本流程的编辑锁占用者（灰度开关默认关）
        flowNotifyService.bindingChanged(target, "bind", product, EditLockSupport.currentUser());
        return toView(saved);
    }

    /** 解绑：ACTIVE → HISTORY（绑定历史保留）。 */
    @Transactional
    public void unbind(String id) {
        CimProductFlow row = requireBinding(id);
        if (!CimProductFlow.BIND_ACTIVE.equals(row.getBindState())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.bind.notActive", row.getBindState());
        }
        row.setBindState(CimProductFlow.BIND_HISTORY);
        bindingRepository.save(row);
        // P3-3：解绑 → 通知该版本流程的编辑锁占用者
        flowRepository.findByFamilyCodeAndDeletedFalse(row.getFlowFamilyCode()).stream()
                .filter(f -> row.getFlowVersionNo().equals(f.getVersionNo()))
                .findFirst()
                .ifPresent(f -> flowNotifyService.bindingChanged(
                        f, "unbind", row.getProductCode(), EditLockSupport.currentUser()));
    }

    /**
     * 列型覆盖更新（V25 L3）：光罩/污染级/禁入/NPW 落绑定行（product+family+version 定位）。
     * 只允许改 ACTIVE 绑定（HISTORY 行是历史事实，不改）。
     */
    @Transactional
    public BindingView updateOverrides(String id, OverrideReq req) {
        CimProductFlow row = requireBinding(id);
        if (!CimProductFlow.BIND_ACTIVE.equals(row.getBindState())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.bind.notActive", row.getBindState());
        }
        if (req.contamLevel() != null && !req.contamLevel().isBlank()
                && req.contamForbidden() != null && !req.contamForbidden().isBlank()) {
            for (String f : req.contamForbidden().split("[,，]")) {
                if (f.trim().equals(req.contamLevel().trim())) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.bind.contamSelfForbidden");
                }
            }
        }
        if (req.npwLimit() != null && req.npwLimit() <= 0) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.flow.npwLimitPositive",
                    row.getProductCode(), req.npwLimit());
        }
        // V32：光罩覆盖引用闭环——非空才校验存在（cim_reticle 先行建，覆盖挂接）
        String reticleRef = req.reticleRef() == null || req.reticleRef().isBlank()
                ? null : req.reticleRef().trim();
        if (reticleRef != null
                && reticleRepository.findByReticleCodeAndDeletedFalse(reticleRef).isEmpty()) {
            throw BizException.of(BizCode.DATA_NOT_FOUND,
                    "mds.biz.bind.reticleNotFound", reticleRef);
        }
        row.setReticleRef(reticleRef);
        row.setContamLevel(req.contamLevel() == null || req.contamLevel().isBlank() ? null : req.contamLevel().trim());
        row.setContamForbidden(req.contamForbidden() == null || req.contamForbidden().isBlank() ? null : req.contamForbidden().trim());
        row.setNpwLimit(req.npwLimit());
        return toView(bindingRepository.save(row));
    }

    /** 供控制器取绑定行（PRODUCT 覆盖端点定位 product+family）。 */
    @Transactional(readOnly = true)
    public CimProductFlow require(String id) {
        return requireBinding(id);
    }

    private CimProductFlow requireBinding(String id) {
        return bindingRepository.findById(id)
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.bind.notFound", id));
    }

    private static BindingView toView(CimProductFlow b) {
        return new BindingView(b.getId(), b.getProductCode(), b.getFlowFamilyCode(),
                b.getFlowVersionNo(), b.getBindState(),
                b.getActiveTime() == null ? null : b.getActiveTime().toString(),
                b.getActiveUser(), b.getIsOccupied(), b.getOccupiedBy(),
                b.getReticleRef(), b.getContamLevel(), b.getContamForbidden(), b.getNpwLimit());
    }
}
