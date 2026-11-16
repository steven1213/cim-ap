package com.cim.mds.server.site;

import java.util.List;
import java.util.Map;

import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 制程技术（Technology）管理——产品管理域（技术节点分类）。
 *
 * <p>权限 {@code mds:tech:*}（控制器层把关）。tech_code 是全局唯一主标识：
 * 建后不可改；节点值（tech_node）是属性可改。将来 Product 落地后此处补
 * 「被产品引用不可删」守门。</p>
 */
@Service
@RequiredArgsConstructor
public class TechnologyAdminService {

    private final CimTechnologyRepository technologyRepository;
    private final CimProductRepository productRepository;
    private final NamingRuleAdminService namingRuleService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record TechnologyView(String id, String techCode, String techName, String techNode,
                                 String remark, Boolean enabled, String description,
                                 Boolean occupied, String occupiedBy) {
    }

    public record TechnologyReq(String techCode, String techName, String techNode,
                                String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<TechnologyView> list(String keyword) {
        return technologyRepository.search(blankToNull(keyword)).stream()
                .map(TechnologyAdminService::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public TechnologyView create(TechnologyReq req) {
        requireText(req.techName(), "techName");
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_TECHNOLOGY,
                req.techCode(), Map.of());
        if (technologyRepository.findByTechCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "制程技术编码已存在: " + code);
        }
        CimTechnology entity = new CimTechnology();
        entity.setTechCode(code);
        apply(entity, req);
        return toView(technologyRepository.save(entity));
    }

    @Transactional
    public TechnologyView update(String id, TechnologyReq req) {
        CimTechnology entity = requireTechnology(id);
        requireText(req.techName(), "techName");
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // techCode 是全局唯一主标识（将来 Product 按 code 引用）：建后不可改
        entity.setTechName(req.techName().trim());
        entity.setTechNode(req.techNode());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(technologyRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimTechnology entity = requireTechnology(id);
        assertNotLockedByOther(entity.getOccupied(), entity.getOccupiedBy());
        // 行业守门：被产品引用的制程技术不可删（V6 起 Product.tech_code 逻辑引用本表）
        if (productRepository.existsByTechCodeAndDeletedFalse(entity.getTechCode())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "制程技术已被产品引用，先解除产品关联再删除: " + entity.getTechCode());
        }
        entity.setDeleted(Boolean.TRUE);
        technologyRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimTechnology requireTechnology(String id) {
        return technologyRepository.findById(id)
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "制程技术不存在: " + id));
    }

    private static void apply(CimTechnology entity, TechnologyReq req) {
        entity.setTechCode(entity.getTechCode() == null ? req.techCode().trim() : entity.getTechCode());
        entity.setTechName(req.techName().trim());
        entity.setTechNode(req.techNode());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private static TechnologyView toView(CimTechnology t) {
        return new TechnologyView(t.getId(), t.getTechCode(), t.getTechName(), t.getTechNode(),
                t.getRemark(), t.getEnabled(), t.getDescription(), t.getOccupied(), t.getOccupiedBy());
    }

    /** 编辑锁守门：被他人占用即拒绝（与位置五层同口径）。 */
    private static void assertNotLockedByOther(boolean occupied, String occupiedBy) {
        if (occupied && occupiedBy != null && !occupiedBy.equals(EditLockSupport.currentUser())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "该记录正在被 " + occupiedBy + " 编辑，暂不能修改或删除");
        }
    }

    /** 保存成功后清编辑锁（与数据保存同一事务）。 */
    private static void clearLock(EditLockDefData entity) {
        entity.setOccupied(Boolean.FALSE);
        entity.setOccupiedBy(null);
        entity.setOccupiedAt(null);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
