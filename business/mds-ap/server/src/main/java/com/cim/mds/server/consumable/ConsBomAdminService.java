package com.cim.mds.server.consumable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cim.mds.server.equipment.CimEquipment;
import com.cim.mds.server.equipment.CimEquipmentRepository;
import com.cim.mds.server.flow.CimFlow;
import com.cim.mds.server.flow.CimFlowRepository;
import com.cim.mds.server.flow.CimOperation;
import com.cim.mds.server.flow.CimOperationRepository;
import com.cim.mds.server.flow.CimParamDef;
import com.cim.mds.server.flow.CimParamDefRepository;
import com.cim.mds.server.site.CimProduct;
import com.cim.mds.server.site.CimProductRepository;
import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.system.dict.SysDictService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 消耗品 BOM 管理——「在哪里、消耗多少」的消耗契约 CRUD（V38）。
 *
 * <p>bom_code 全局唯一、建后不可改；头表编辑锁三列，行表全量替换轻量维护。
 * 守门规则：spec 必须 ACTIVE 且 material_kind=CONSUMABLE（DURABLE 挡 BOM 误引用、
 * OBSOLETE 挡新引用）；VARIABLE 行 param_key 必须在 cim_param_def 注册且启用；
 * 同头同 spec 唯一；激活时行数非空、同上下文旧 ACTIVE 自动画 SUPERSEDED、
 * version_no 取同上下文 max+1、source_op_version 快照工序当前版本。铁律：
 * ACTIVE 不回 DRAFT（改=复制新 BOM）、SUPERSEDED/DISABLED 不可删、
 * ACTIVE/SUPERSEDED 行不可改。库存扣减/齐套/拣配归 MES/ERP 运行时。</p>
 */
@Service
@RequiredArgsConstructor
public class ConsBomAdminService {

    public static final String DICT_CONSUME_TYPE = "mds.cons_consume_type";
    public static final String DICT_CONS_BASIS = "mds.cons_basis";
    public static final String DICT_EXPIRY_POLICY = "mds.cons_expiry_policy";

    private final CimConsBomRepository bomRepository;
    private final CimConsBomLineRepository lineRepository;
    private final CimConsumableRepository specRepository;
    private final CimProductRepository productRepository;
    private final CimFlowRepository flowRepository;
    private final CimOperationRepository operationRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final CimParamDefRepository paramDefRepository;
    private final SysDictService dictService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record LineView(String id, String specId, String specCode, String specName,
                           String consumeType, String basis, java.math.BigDecimal stdQty,
                           java.math.BigDecimal tolerancePct, java.math.BigDecimal lossFactor,
                           String paramKey, java.math.BigDecimal paramQtyPerUnit,
                           String substituteGroup, Integer subPriority, String expiryPolicy,
                           Integer sortNo, String note) {
    }

    public record BomView(String id, String bomCode, String bomName,
                          String productId, String productCode,
                          String flowId, String flowCode,
                          String operationId, String operationCode, Integer opVersionNo,
                          String equipmentId, String equipmentCode,
                          String status, Integer versionNo, Integer sourceOpVersion,
                          Boolean enabled, int lineCount,
                          Boolean occupied, String occupiedBy) {
    }

    public record LineReq(String specId, String consumeType, String basis,
                          java.math.BigDecimal stdQty, java.math.BigDecimal tolerancePct,
                          java.math.BigDecimal lossFactor, String paramKey,
                          java.math.BigDecimal paramQtyPerUnit, String substituteGroup,
                          Integer subPriority, String expiryPolicy, Integer sortNo, String note) {
    }

    public record BomReq(String bomCode, String bomName, String productId, String flowId,
                         String operationId, String equipmentId, Boolean enabled) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<BomView> list(String productId, String flowId, String operationId,
                              String status, String keyword) {
        return bomRepository.search(blankToNull(productId), blankToNull(flowId),
                blankToNull(operationId), blankToNull(status), blankToNull(keyword)).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LineView> lines(String bomId) {
        requireBom(bomId);
        return toLineViews(lineRepository.findByBomIdAndDeletedFalseOrderBySortNoAsc(bomId));
    }

    // ------------------------------------------------------------------ 写（头）

    @Transactional
    public BomView create(BomReq req) {
        requireText(req.bomCode(), "bomCode");
        requireText(req.bomName(), "bomName");
        String code = req.bomCode().trim();
        if (bomRepository.existsByBomCodeAndDeletedFalse(code)) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.consBom.codeDuplicate", code);
        }
        OpRef opRef = requireContext(req);
        CimConsBom entity = new CimConsBom();
        entity.setBomCode(code);
        applyContext(entity, req);
        entity.setEnabled(req.enabled() == null || req.enabled());
        entity.setStatus(CimConsBom.STATUS_DRAFT);
        // 工序当前版本快照（草稿期预记，激活时刷新）
        entity.setSourceOpVersion(opRef.versionNo());
        return toView(bomRepository.save(entity));
    }

    /** 更新头（仅 DRAFT：ACTIVE 不回 DRAFT 铁律；绑定上下文改动重校验引用）。 */
    @Transactional
    public BomView update(String id, BomReq req) {
        CimConsBom entity = requireBom(id);
        requireText(req.bomName(), "bomName");
        if (!CimConsBom.STATUS_DRAFT.equals(entity.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.consBom.statusEditNotAllowed", entity.getStatus());
        }
        requireContext(req);
        assertNotLockedByOther(entity);
        applyContext(entity, req);
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(bomRepository.save(entity));
    }

    /**
     * 激活（DRAFT → ACTIVE）：行数非空、同上下文旧 ACTIVE 自动画 SUPERSEDED、
     * version_no 取同上下文 max+1、source_op_version 刷新为工序当前版本。
     */
    @Transactional
    public BomView activate(String id) {
        CimConsBom entity = requireBom(id);
        if (!CimConsBom.STATUS_DRAFT.equals(entity.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.consBom.activateNotAllowed", entity.getStatus());
        }
        if (lineRepository.countByBomIdAndDeletedFalse(id) == 0) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.consBom.activateNoLines");
        }
        assertNotLockedByOther(entity);
        List<CimConsBom> family = bomRepository
                .findByProductIdAndFlowIdAndOperationIdAndDeletedFalse(
                        entity.getProductId(), entity.getFlowId(), entity.getOperationId());
        family.stream()
                .filter(b -> !b.getId().equals(id))
                .filter(b -> CimConsBom.STATUS_ACTIVE.equals(b.getStatus())
                        || CimConsBom.STATUS_SUPERSEDED.equals(b.getStatus()))
                .forEach(b -> b.setStatus(CimConsBom.STATUS_SUPERSEDED));
        int nextVersion = family.stream().mapToInt(b -> b.getVersionNo() == null ? 0 : b.getVersionNo())
                .max().orElse(0) + 1;
        entity.setVersionNo(nextVersion);
        entity.setSourceOpVersion(requireOperation(entity.getOperationId()).getVersionNo());
        entity.setStatus(CimConsBom.STATUS_ACTIVE);
        return toView(bomRepository.save(entity));
    }

    /** 手动停用（ACTIVE → DISABLED）。 */
    @Transactional
    public BomView disable(String id) {
        CimConsBom entity = requireBom(id);
        if (!CimConsBom.STATUS_ACTIVE.equals(entity.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.consBom.disableNotAllowed", entity.getStatus());
        }
        assertNotLockedByOther(entity);
        entity.setStatus(CimConsBom.STATUS_DISABLED);
        clearLock(entity);
        return toView(bomRepository.save(entity));
    }

    /** 删除（仅 DRAFT/DISABLED；SUPERSEDED 留 as-built 追溯、ACTIVE 先停用；级联清明细）。 */
    @Transactional
    public void delete(String id) {
        CimConsBom entity = requireBom(id);
        String st = entity.getStatus();
        if (CimConsBom.STATUS_ACTIVE.equals(st) || CimConsBom.STATUS_SUPERSEDED.equals(st)) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.consBom.deleteNotAllowed", st);
        }
        assertNotLockedByOther(entity);
        lineRepository.findByBomIdAndDeletedFalseOrderBySortNoAsc(id)
                .forEach(line -> line.setDeleted(Boolean.TRUE));
        lineRepository.flush();
        entity.setDeleted(Boolean.TRUE);
        bomRepository.save(entity);
    }

    // ------------------------------------------------------------------ 写（明细全量替换）

    /** 全量替换明细行（仅 DRAFT；先删后插必 flush）。 */
    @Transactional
    public List<LineView> replaceLines(String bomId, List<LineReq> lines) {
        CimConsBom bom = requireBom(bomId);
        if (!CimConsBom.STATUS_DRAFT.equals(bom.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.consBom.statusEditNotAllowed", bom.getStatus());
        }
        Set<String> specIds = new HashSet<>();
        for (LineReq line : lines) {
            CimConsumable spec = requireSpec(line.specId());
            requireText(line.consumeType(), "consumeType");
            requireText(line.basis(), "basis");
            requireDictItem(DICT_CONSUME_TYPE, line.consumeType());
            requireDictItem(DICT_CONS_BASIS, line.basis());
            requireDictItem(DICT_EXPIRY_POLICY, line.expiryPolicy());
            if (line.stdQty() == null || line.stdQty().signum() <= 0) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.consBom.stdQtyInvalid", spec.getConsCode());
            }
            if (line.tolerancePct() != null
                    && (line.tolerancePct().signum() < 0
                        || line.tolerancePct().compareTo(java.math.BigDecimal.valueOf(100)) > 0)) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.consBom.toleranceInvalid", spec.getConsCode());
            }
            if (CimConsBomLine.CT_VARIABLE.equals(trimToNull(line.consumeType()))) {
                String paramKey = trimToNull(line.paramKey());
                if (paramKey == null) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.consBom.paramRequired", spec.getConsCode());
                }
                CimParamDef def = paramDefRepository.findByAttrKeyAndDeletedFalse(paramKey)
                        .orElseThrow(() -> BizException.of(BizCode.PARAM_INVALID,
                                "mds.biz.consBom.paramInvalid", paramKey));
                if (!Boolean.TRUE.equals(def.getEnabled())) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.consBom.paramInvalid", paramKey);
                }
                if (line.paramQtyPerUnit() == null || line.paramQtyPerUnit().signum() <= 0) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.consBom.paramQtyInvalid", paramKey);
                }
            } else if (CimConsBomLine.CT_FIXED.equals(trimToNull(line.consumeType()))
                    && line.paramKey() != null) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.consBom.fixedNoParam", spec.getConsCode());
            }
            if (!specIds.add(line.specId().trim())) {
                throw BizException.of(BizCode.DATA_DUPLICATE,
                        "mds.biz.consBom.specDuplicate", spec.getConsCode());
            }
        }
        List<CimConsBomLine> old = lineRepository.findByBomIdAndDeletedFalseOrderBySortNoAsc(bomId);
        old.forEach(l -> l.setDeleted(Boolean.TRUE));
        lineRepository.flush();
        List<CimConsBomLine> saved = new ArrayList<>();
        int sort = 0;
        for (LineReq req : lines) {
            CimConsBomLine line = new CimConsBomLine();
            line.setBomId(bomId);
            line.setSpecId(req.specId().trim());
            line.setConsumeType(trimToNull(req.consumeType()));
            line.setBasis(trimToNull(req.basis()));
            line.setStdQty(req.stdQty());
            line.setTolerancePct(req.tolerancePct());
            line.setLossFactor(req.lossFactor());
            line.setParamKey(trimToNull(req.paramKey()));
            line.setParamQtyPerUnit(req.paramQtyPerUnit());
            line.setSubstituteGroup(trimToNull(req.substituteGroup()));
            line.setSubPriority(req.subPriority());
            line.setExpiryPolicy(trimToNull(req.expiryPolicy()));
            line.setSortNo(req.sortNo() == null ? ++sort * 10 : req.sortNo());
            line.setNote(trimToNull(req.note()));
            saved.add(lineRepository.save(line));
        }
        return toLineViews(saved);
    }

    // ------------------------------------------------------------------ 内部

    private record OpRef(Integer versionNo) {
    }

    /** 绑定上下文引用校验：产品/流程/工序必填且存在；机台可空非空须存在。返回工序当前版本。 */
    private OpRef requireContext(BomReq req) {
        requireText(req.productId(), "productId");
        requireText(req.flowId(), "flowId");
        requireText(req.operationId(), "operationId");
        if (productRepository.findById(req.productId()).filter(p -> !Boolean.TRUE.equals(p.getDeleted())).isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.consBom.refMissing", "productId");
        }
        if (flowRepository.findById(req.flowId()).filter(f -> !Boolean.TRUE.equals(f.getDeleted())).isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.consBom.refMissing", "flowId");
        }
        Integer opVersion = requireOperation(req.operationId()).getVersionNo();
        if (trimToNull(req.equipmentId()) != null
                && equipmentRepository.findById(req.equipmentId())
                        .filter(e -> !Boolean.TRUE.equals(e.getDeleted())).isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.consBom.refMissing", "equipmentId");
        }
        return new OpRef(opVersion);
    }

    private void applyContext(CimConsBom entity, BomReq req) {
        entity.setBomName(req.bomName().trim());
        entity.setProductId(req.productId().trim());
        entity.setFlowId(req.flowId().trim());
        entity.setOperationId(req.operationId().trim());
        entity.setEquipmentId(trimToNull(req.equipmentId()));
    }

    /** 规格守门：存在、未删、ACTIVE、material_kind=CONSUMABLE。 */
    private CimConsumable requireSpec(String specId) {
        requireText(specId, "specId");
        CimConsumable spec = specRepository.findById(specId)
                .filter(s -> !Boolean.TRUE.equals(s.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.consBom.specNotFound", specId));
        if (!CimConsumable.LC_ACTIVE.equals(spec.getLifecycle())) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.consBom.specInvalid", spec.getConsCode(), spec.getLifecycle());
        }
        if (!CimConsumable.KIND_CONSUMABLE.equals(spec.getMaterialKind())) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.consBom.specInvalid", spec.getConsCode(), spec.getMaterialKind());
        }
        return spec;
    }

    private CimOperation requireOperation(String operationId) {
        return operationRepository.findById(operationId)
                .filter(o -> !Boolean.TRUE.equals(o.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.consBom.refMissing", "operationId"));
    }

    private CimConsBom requireBom(String id) {
        return bomRepository.findById(id)
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.consBom.notFound", id));
    }

    private BomView toView(CimConsBom b) {
        CimProduct product = productRepository.findById(b.getProductId()).orElse(null);
        CimFlow flow = flowRepository.findById(b.getFlowId()).orElse(null);
        CimOperation op = operationRepository.findById(b.getOperationId()).orElse(null);
        CimEquipment eqp = b.getEquipmentId() == null ? null
                : equipmentRepository.findById(b.getEquipmentId()).orElse(null);
        return new BomView(b.getId(), b.getBomCode(), b.getBomName(),
                b.getProductId(), product == null ? null : product.getProductCode(),
                b.getFlowId(), flow == null ? null : flow.getFlowCode(),
                b.getOperationId(), op == null ? null : op.getOpCode(),
                op == null ? null : op.getVersionNo(),
                b.getEquipmentId(), eqp == null ? null : eqp.getEquipmentCode(),
                b.getStatus(), b.getVersionNo(), b.getSourceOpVersion(), b.getEnabled(),
                (int) lineRepository.countByBomIdAndDeletedFalse(b.getId()),
                b.getOccupied(), b.getOccupiedBy());
    }

    /** 行视图：批量取规格编码/名称（前端展示用）。 */
    private List<LineView> toLineViews(List<CimConsBomLine> lines) {
        Set<String> ids = lines.stream().map(CimConsBomLine::getSpecId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, CimConsumable> specs = new java.util.HashMap<>();
        for (String specId : ids) {
            specRepository.findById(specId)
                    .filter(s -> !Boolean.TRUE.equals(s.getDeleted()))
                    .ifPresent(s -> specs.put(s.getId(), s));
        }
        return lines.stream().map(l -> {
            CimConsumable s = specs.get(l.getSpecId());
            return new LineView(l.getId(), l.getSpecId(),
                    s == null ? null : s.getConsCode(), s == null ? null : s.getConsName(),
                    l.getConsumeType(), l.getBasis(), l.getStdQty(), l.getTolerancePct(),
                    l.getLossFactor(), l.getParamKey(), l.getParamQtyPerUnit(),
                    l.getSubstituteGroup(), l.getSubPriority(), l.getExpiryPolicy(),
                    l.getSortNo(), l.getNote());
        }).toList();
    }

    private void requireDictItem(String dictCode, String value) {
        String v = trimToNull(value);
        if (v == null) {
            return;
        }
        boolean hit = dictService.items(dictCode).stream().anyMatch(i -> v.equals(i.getItemKey()));
        if (!hit) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.consBom.dictInvalid",
                    dictCode, v);
        }
    }

    private static void assertNotLockedByOther(EditLockDefData entity) {
        if (Boolean.TRUE.equals(entity.getOccupied())
                && entity.getOccupiedBy() != null
                && !entity.getOccupiedBy().equals(EditLockSupport.currentUser())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "该记录正在被 " + entity.getOccupiedBy() + " 编辑，暂不能修改或删除");
        }
    }

    private static void clearLock(EditLockDefData entity) {
        entity.setOccupied(Boolean.FALSE);
        entity.setOccupiedBy(null);
        entity.setOccupiedAt(null);
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", field);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
