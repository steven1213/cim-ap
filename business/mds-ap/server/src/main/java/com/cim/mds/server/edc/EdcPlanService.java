package com.cim.mds.server.edc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cim.mds.server.flow.CimFlow;
import com.cim.mds.server.flow.CimFlowRepository;
import com.cim.mds.server.flow.CimOperationRepository;
import com.cim.mds.server.site.CimProductRepository;
import com.cim.mds.server.site.EditLockSupport;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EDC 方案服务（V28）——版本化生命周期 + 明细（采集项 × Measurement Spec）维护。
 *
 * <p>生命周期镜像 CimFlow 四态：UNFROZEN→FROZEN→ACTIVE→DISABLED；
 * activate 自动降级同族旧 ACTIVE（单 Active 约束）；明细仅 UNFROZEN 可维护
 * （发布数据只读，与流程覆盖同口径）。编辑锁 type=edcPlan。</p>
 */
@Service
@RequiredArgsConstructor
public class EdcPlanService {

    private final CimEdcPlanRepository planRepository;
    private final CimEdcPlanItemRepository itemRepository;
    private final EdcAdminService adminService;
    private final CimProductRepository productRepository;
    private final CimFlowRepository flowRepository;
    private final CimOperationRepository operationRepository;

    // ================================================================ views

    public record PlanItemView(String id, String attrKey, String collectMode, String sampleRule,
                               Integer intervalMinutes, Boolean required, BigDecimal target,
                               BigDecimal lsl, BigDecimal usl, String oocAction, Integer sortNo,
                               EdcAdminService.ParamLite param) {
    }

    public record PlanView(String id, String planCode, Integer versionNo, String status,
                           String productCode, String flowFamilyCode, Integer flowVersionNo,
                           String opCode, String setCode, String description,
                           String activatedAt, String activatedBy,
                           Boolean occupied, String occupiedBy, List<PlanItemView> items) {
    }

    public record PlanReq(String planCode, Integer versionNo, String productCode,
                          String flowFamilyCode, Integer flowVersionNo, String opCode,
                          String setCode, String description) {
    }

    public record PlanItemReq(String attrKey, String collectMode, String sampleRule,
                              Integer intervalMinutes, Boolean required, BigDecimal target,
                              BigDecimal lsl, BigDecimal usl, String oocAction, Integer sortNo) {
    }

    // ================================================================ CRUD

    @Transactional(readOnly = true)
    public List<CimEdcPlan> list() {
        return planRepository.findByDeletedFalseOrderByPlanCodeAscVersionNoDesc();
    }

    @Transactional(readOnly = true)
    public PlanView get(String id) {
        return toView(requirePlan(id));
    }

    @Transactional
    public CimEdcPlan create(PlanReq req) {
        String planCode = EdcAdminService.requireText(req.planCode(), "planCode");
        String family = EdcAdminService.requireText(req.flowFamilyCode(), "flowFamilyCode");
        Integer flowVersion = req.flowVersionNo() == null ? 1 : req.flowVersionNo();
        String opCode = EdcAdminService.requireText(req.opCode(), "opCode");
        if (req.productCode() != null && !req.productCode().isBlank()) {
            productRepository.findByProductCodeAndDeletedFalse(req.productCode().trim())
                    .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                            "mds.biz.bind.productNotFound", req.productCode()));
        }
        flowRepository.findByFamilyCodeAndVersionNoAndDeletedFalse(family, flowVersion)
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.bind.flowVersionNotFound", family, flowVersion));
        operationRepository.findByOpCodeAndDeletedFalse(opCode)
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.edc.opNotFound", opCode));
        int versionNo = req.versionNo() == null
                ? planRepository.findByPlanCodeAndDeletedFalseOrderByVersionNoDesc(planCode)
                        .stream().findFirst().map(p -> p.getVersionNo() + 1).orElse(1)
                : req.versionNo();
        if (planRepository.findByPlanCodeAndVersionNoAndDeletedFalse(planCode, versionNo).isPresent()) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.edc.planDuplicate", planCode, versionNo);
        }
        CimEdcPlan entity = new CimEdcPlan();
        entity.setPlanCode(planCode);
        entity.setVersionNo(versionNo);
        entity.setStatus(CimEdcPlan.STATUS_UNFROZEN);
        entity.setProductCode(EdcAdminService.blankToNull(req.productCode()));
        entity.setFlowFamilyCode(family);
        entity.setFlowVersionNo(flowVersion);
        entity.setOpCode(opCode);
        entity.setSetCode(EdcAdminService.blankToNull(req.setCode()));
        entity.setDescription(req.description());
        return planRepository.save(entity);
    }

    @Transactional
    public CimEdcPlan update(String id, PlanReq req) {
        CimEdcPlan entity = requirePlan(id);
        EdcAdminService.assertNotLockedByOther(entity);
        if (!CimEdcPlan.STATUS_UNFROZEN.equals(entity.getStatus())
                && !CimEdcPlan.STATUS_DISABLED.equals(entity.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.planNotEditable", entity.getPlanCode(), entity.getStatus());
        }
        // 绑定维度变更同样过存在性守门（planCode/versionNo 建后不可改）
        if (req.productCode() != null && !req.productCode().isBlank()) {
            productRepository.findByProductCodeAndDeletedFalse(req.productCode().trim())
                    .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                            "mds.biz.bind.productNotFound", req.productCode()));
        }
        entity.setProductCode(EdcAdminService.blankToNull(req.productCode()));
        entity.setFlowFamilyCode(EdcAdminService.requireText(req.flowFamilyCode(), "flowFamilyCode"));
        entity.setFlowVersionNo(req.flowVersionNo() == null ? entity.getFlowVersionNo() : req.flowVersionNo());
        entity.setOpCode(EdcAdminService.requireText(req.opCode(), "opCode"));
        entity.setSetCode(EdcAdminService.blankToNull(req.setCode()));
        entity.setDescription(req.description());
        EdcAdminService.clearLock(entity);
        return planRepository.save(entity);
    }

    @Transactional
    public void delete(String id) {
        CimEdcPlan entity = requirePlan(id);
        EdcAdminService.assertNotLockedByOther(entity);
        if (!CimEdcPlan.STATUS_UNFROZEN.equals(entity.getStatus())
                && !CimEdcPlan.STATUS_DISABLED.equals(entity.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.statusForbidden", entity.getPlanCode(), entity.getStatus());
        }
        itemRepository.deleteByPlanId(id);
        entity.setDeleted(Boolean.TRUE);
        planRepository.save(entity);
    }

    // ================================================================ 生命周期

    @Transactional
    public CimEdcPlan freeze(String id) {
        CimEdcPlan plan = requirePlan(id);
        if (!CimEdcPlan.STATUS_UNFROZEN.equals(plan.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.statusForbidden", plan.getPlanCode(), plan.getStatus());
        }
        if (!itemRepository.existsByPlanIdAndDeletedFalse(plan.getId())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.freezeNeedItems", plan.getPlanCode());
        }
        plan.setStatus(CimEdcPlan.STATUS_FROZEN);
        return planRepository.save(plan);
    }

    @Transactional
    public CimEdcPlan unfreeze(String id) {
        CimEdcPlan plan = requirePlan(id);
        if (!CimEdcPlan.STATUS_FROZEN.equals(plan.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.statusForbidden", plan.getPlanCode(), plan.getStatus());
        }
        plan.setStatus(CimEdcPlan.STATUS_UNFROZEN);
        return planRepository.save(plan);
    }

    @Transactional
    public CimEdcPlan activate(String id) {
        CimEdcPlan plan = requirePlan(id);
        if (!CimEdcPlan.STATUS_FROZEN.equals(plan.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.statusForbidden", plan.getPlanCode(), plan.getStatus());
        }
        // 单 Active 约束：同族旧 ACTIVE 自动降级 FROZEN（镜像 Flow activate）
        planRepository.findByPlanCodeAndDeletedFalseAndStatus(plan.getPlanCode(), CimEdcPlan.STATUS_ACTIVE)
                .filter(old -> !old.getId().equals(plan.getId()))
                .ifPresent(old -> {
                    old.setStatus(CimEdcPlan.STATUS_FROZEN);
                    planRepository.save(old);
                });
        plan.setStatus(CimEdcPlan.STATUS_ACTIVE);
        plan.setActivatedAt(LocalDateTime.now());
        plan.setActivatedBy(EditLockSupport.currentUser());
        return planRepository.save(plan);
    }

    @Transactional
    public CimEdcPlan disable(String id) {
        CimEdcPlan plan = requirePlan(id);
        if (!CimEdcPlan.STATUS_FROZEN.equals(plan.getStatus())
                && !CimEdcPlan.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.statusForbidden", plan.getPlanCode(), plan.getStatus());
        }
        plan.setStatus(CimEdcPlan.STATUS_DISABLED);
        return planRepository.save(plan);
    }

    // ================================================================ 明细

    /** 明细整单替换（replace-all）。 */
    @Transactional
    public PlanView saveItems(String id, List<PlanItemReq> items) {
        CimEdcPlan plan = requirePlan(id);
        if (!CimEdcPlan.STATUS_UNFROZEN.equals(plan.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.itemsEditableOnlyUnfrozen", plan.getPlanCode(), plan.getStatus());
        }
        List<CimEdcPlanItem> fresh = validateItems(items, plan.getId());
        // 先删后插：flush 强制 DELETE 先落库，否则 Hibernate 把 DELETE 排到 INSERT
        // 之后会撞 uk_edc_plan_item（整单替换语义）
        itemRepository.deleteByPlanId(id);
        itemRepository.flush();
        itemRepository.saveAll(fresh);
        EdcAdminService.clearLock(plan);
        return toView(planRepository.save(plan));
    }

    /** 从参数集一键带入明细（默认采集属性 + 参数集默认无规格限，UNFROZEN 才可）。 */
    @Transactional
    public PlanView applySet(String id, String setCode) {
        CimEdcPlan plan = requirePlan(id);
        if (!CimEdcPlan.STATUS_UNFROZEN.equals(plan.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.itemsEditableOnlyUnfrozen", plan.getPlanCode(), plan.getStatus());
        }
        CimEdcParamSet set = adminService.listSets().stream()
                .filter(s -> setCode.equals(s.getSetCode()))
                .findFirst()
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.edc.setNotFound", setCode));
        List<PlanItemReq> items = new ArrayList<>();
        int sort = 0;
        for (CimEdcParamSetItem member : adminService.setMembers(set.getId())) {
            items.add(new PlanItemReq(member.getAttrKey(), CimEdcPlanItem.CM_AUTO,
                    CimEdcPlanItem.SR_BY_LOT, null, Boolean.FALSE, null, null, null,
                    CimEdcPlanItem.OA_NONE, sort));
            sort++;
        }
        List<CimEdcPlanItem> fresh = validateItems(items, plan.getId());
        itemRepository.deleteByPlanId(id);
        itemRepository.flush();
        itemRepository.saveAll(fresh);
        plan.setSetCode(setCode);
        EdcAdminService.clearLock(plan);
        return toView(planRepository.save(plan));
    }

    /** 明细逐行校验（服务层守门共用：导入也走这里）。 */
    List<CimEdcPlanItem> validateItems(List<PlanItemReq> items, String planId) {
        Set<String> seen = new HashSet<>();
        List<CimEdcPlanItem> fresh = new ArrayList<>();
        int autoSort = 0;
        for (PlanItemReq r : items == null ? List.<PlanItemReq>of() : items) {
            String key = EdcAdminService.requireText(r.attrKey(), "attrKey");
            adminService.requireParam(key);
            if (!seen.add(key)) {
                throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.edc.attrDuplicate", key);
            }
            String mode = r.collectMode() == null || r.collectMode().isBlank()
                    ? CimEdcPlanItem.CM_AUTO : r.collectMode().trim();
            if (!CimEdcPlanItem.CM_AUTO.equals(mode) && !CimEdcPlanItem.CM_MANUAL.equals(mode)
                    && !CimEdcPlanItem.CM_SEMI.equals(mode)) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.collectModeInvalid", key);
            }
            String rule = r.sampleRule() == null || r.sampleRule().isBlank()
                    ? CimEdcPlanItem.SR_BY_LOT : r.sampleRule().trim();
            if (!CimEdcPlanItem.SR_BY_LOT.equals(rule) && !CimEdcPlanItem.SR_BY_WAFER.equals(rule)
                    && !CimEdcPlanItem.SR_BY_TIME.equals(rule) && !CimEdcPlanItem.SR_BY_EVENT.equals(rule)) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.sampleRuleInvalid", key);
            }
            if (CimEdcPlanItem.SR_BY_TIME.equals(rule) && (r.intervalMinutes() == null || r.intervalMinutes() <= 0)) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.intervalRequired", key);
            }
            String action = r.oocAction() == null || r.oocAction().isBlank()
                    ? CimEdcPlanItem.OA_NONE : r.oocAction().trim();
            if (!CimEdcPlanItem.OA_NONE.equals(action) && !CimEdcPlanItem.OA_HOLD.equals(action)
                    && !CimEdcPlanItem.OA_NOTIFY.equals(action)) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.oocActionInvalid", key);
            }
            if (r.lsl() != null && r.usl() != null && r.lsl().compareTo(r.usl()) >= 0) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.limitInvalid", key);
            }
            CimEdcPlanItem item = new CimEdcPlanItem();
            item.setPlanId(planId);
            item.setAttrKey(key);
            item.setCollectMode(mode);
            item.setSampleRule(rule);
            item.setIntervalMinutes(r.intervalMinutes());
            item.setRequired(Boolean.TRUE.equals(r.required()));
            item.setTarget(r.target());
            item.setLsl(r.lsl());
            item.setUsl(r.usl());
            item.setOocAction(action);
            item.setSortNo(r.sortNo() == null ? autoSort : r.sortNo());
            fresh.add(item);
            autoSort++;
        }
        return fresh;
    }

    // ================================================================ 内部

    private CimEdcPlan requirePlan(String id) {
        return planRepository.findById(id)
                .filter(p -> !Boolean.TRUE.equals(p.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.edc.planNotFound", id));
    }

    private PlanView toView(CimEdcPlan p) {
        List<PlanItemView> items = itemRepository
                .findByPlanIdAndDeletedFalseOrderBySortNoAsc(p.getId()).stream()
                .map(i -> new PlanItemView(i.getId(), i.getAttrKey(), i.getCollectMode(),
                        i.getSampleRule(), i.getIntervalMinutes(), i.getRequired(), i.getTarget(),
                        i.getLsl(), i.getUsl(), i.getOocAction(), i.getSortNo(),
                        adminService.paramLite(i.getAttrKey())))
                .toList();
        return new PlanView(p.getId(), p.getPlanCode(), p.getVersionNo(), p.getStatus(),
                p.getProductCode(), p.getFlowFamilyCode(), p.getFlowVersionNo(), p.getOpCode(),
                p.getSetCode(), p.getDescription(),
                p.getActivatedAt() == null ? null : p.getActivatedAt().toString(),
                p.getActivatedBy(), p.getOccupied(), p.getOccupiedBy(), items);
    }
}
