package com.cim.mds.server.flow;

import java.util.List;
import java.util.Set;

import com.cim.mds.server.equipment.CimEquipment;
import com.cim.mds.server.equipment.CimEquipmentClassRepository;
import com.cim.mds.server.equipment.CimEquipmentRepository;
import com.cim.mds.server.equipment.CimToolGroupRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一指派服务（ICM 核心）——scope=OPERATION/FLOW/STEP 三上下文一套 CRUD。
 *
 * <p>同一张表/同一套服务覆盖 ContextChain 三层（P3 加 PRODUCT 只扩枚举）：
 * L0 工序默认指派（{@code /operations/{opCode}/assigns}）、L1 流程级覆盖指派
 * （{@code /flows/{flowId}/assigns}）、L2 节点覆盖指派
 * （{@code /flows/{flowId}/steps/{stepId}/assigns}，stepId = node_id）。</p>
 *
 * <p>守门：scope 与上下文列匹配（FLOW 级 step_id 必空串）；assign_type 三类引用
 * 存在且 enabled；recipe_rule=FIXED → recipe_ref 必填；EXACT_MATCH → 合法 JSON；
 * uk_ea 重复（同 scope/上下文/类型/引用/优先级）→ 2004；
 * <b>四态只读</b>：scope=FLOW/STEP 行所属流程 status≠UNFROZEN 禁改禁删（2004）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipAssignAdminService {

    private static final Set<String> VALID_SCOPES = Set.of(
            CimEquipAssign.SCOPE_OPERATION, CimEquipAssign.SCOPE_FLOW, CimEquipAssign.SCOPE_STEP,
            CimEquipAssign.SCOPE_PRODUCT);
    private static final Set<String> VALID_TYPES = Set.of(
            CimEquipAssign.TYPE_GROUP, CimEquipAssign.TYPE_EQUIP, CimEquipAssign.TYPE_CLASS);
    private static final Set<String> VALID_RECIPE_RULES = Set.of(
            CimEquipAssign.RULE_FIXED, CimEquipAssign.RULE_GENERATE, CimEquipAssign.RULE_EXACT_MATCH);

    private final CimEquipAssignRepository assignRepository;
    private final CimFlowRepository flowRepository;
    private final CimFlowStepRepository stepRepository;
    private final CimOperationRepository operationRepository;
    private final CimToolGroupRepository toolGroupRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final CimEquipmentClassRepository equipmentClassRepository;
    private final ObjectMapper objectMapper;
    private final FlowEventService flowEventService;

    /** 请求体（三 scope 通用；上下文键由端点路径决定，body 不再传）。 */
    public record AssignReq(String assignType, String assignRef, String chamberRule,
                            String recipeRef, String recipeRule, String recipeMatch,
                            Integer priority, Boolean enabled,
                            String remark, String description) {
    }

    // ---------------------------------------------------------------- L0 工序默认指派

    @Transactional(readOnly = true)
    public List<CimEquipAssign> listByOperation(String opCode) {
        return assignRepository
                .findByScopeTypeAndOperationCodeAndDeletedFalse(CimEquipAssign.SCOPE_OPERATION, opCode);
    }

    @Transactional
    public CimEquipAssign createForOperation(String opCode, AssignReq req) {
        CimOperation op = requireOperation(opCode);
        return doCreate(CimEquipAssign.SCOPE_OPERATION, op.getOpCode(), "", "", req);
    }

    // ---------------------------------------------------------------- L1 流程级覆盖指派

    @Transactional(readOnly = true)
    public List<CimEquipAssign> listByFlow(String flowId) {
        return assignRepository
                .findByScopeTypeAndFlowIdAndDeletedFalse(CimEquipAssign.SCOPE_FLOW, flowId);
    }

    @Transactional
    public CimEquipAssign createForFlow(String flowId, AssignReq req) {
        requireEditableFlow(flowId);
        CimEquipAssign saved = doCreate(CimEquipAssign.SCOPE_FLOW, "", flowId, "", req);
        recordAssign(saved, "CREATE");
        return saved;
    }

    // ---------------------------------------------------------------- L2 节点覆盖指派

    @Transactional(readOnly = true)
    public List<CimEquipAssign> listByStep(String flowId, String nodeId) {
        requireStep(flowId, nodeId);
        return assignRepository.findByScopeTypeAndFlowIdAndStepIdAndDeletedFalse(
                CimEquipAssign.SCOPE_STEP, flowId, nodeId);
    }

    @Transactional
    public CimEquipAssign createForStep(String flowId, String nodeId, AssignReq req) {
        requireStep(flowId, nodeId);
        requireEditableFlow(flowId);
        CimEquipAssign saved = doCreate(CimEquipAssign.SCOPE_STEP, "", flowId, nodeId, req);
        recordAssign(saved, "CREATE");
        return saved;
    }

    // ---------------------------------------------------------------- L3 产品级覆盖指派（V25）

    /** PRODUCT 层列表（product_code+family_code 定位；stepId 非空 = 细化到站行）。 */
    @Transactional(readOnly = true)
    public List<CimEquipAssign> listByProduct(String productCode, String familyCode) {
        return assignRepository.findByScopeTypeAndProductCodeAndFamilyCodeAndDeletedFalse(
                CimEquipAssign.SCOPE_PRODUCT, productCode, familyCode);
    }

    /** PRODUCT 层新建（productCode/familyCode 必填；nodeId 可空 = 整条流程生效）。
     *  产品覆盖独立于流程状态（产品域配置），不做 UNFROZEN 守门。 */
    @Transactional
    public CimEquipAssign createForProduct(String productCode, String familyCode,
                                           String nodeId, AssignReq req) {
        CimEquipAssign entity = new CimEquipAssign();
        entity.setScopeType(CimEquipAssign.SCOPE_PRODUCT);
        entity.setProductCode(productCode == null ? "" : productCode.trim());
        entity.setFamilyCode(familyCode == null ? "" : familyCode.trim());
        entity.setStepId(nodeId == null ? "" : nodeId.trim());
        applyFields(entity, req);
        assertContextValid(entity);
        assertNotDuplicate(entity, null);
        return assignRepository.save(entity);
    }

    // ---------------------------------------------------------------- 通用 UPDATE / DELETE

    @Transactional
    public CimEquipAssign update(String id, AssignReq req) {
        CimEquipAssign entity = requireAssign(id);
        requireEditable(entity);
        applyFields(entity, req);
        assertContextValid(entity);
        assertNotDuplicate(entity, id);
        CimEquipAssign saved = assignRepository.save(entity);
        recordAssign(saved, "UPDATE");
        return saved;
    }

    @Transactional
    public void delete(String id) {
        CimEquipAssign entity = requireAssign(id);
        requireEditable(entity);
        recordAssign(entity, "DELETE");
        assignRepository.delete(entity);
    }

    // ---------------------------------------------------------------- 内部

    /** 事件历史（V24）：FLOW/STEP 层指派变更落流程锚点事件；OPERATION/PRODUCT 层无流程锚点，不落。 */
    private void recordAssign(CimEquipAssign entity, String action) {
        if (!CimEquipAssign.SCOPE_FLOW.equals(entity.getScopeType())
                && !CimEquipAssign.SCOPE_STEP.equals(entity.getScopeType())) {
            return;
        }
        String flowCode = flowRepository.findById(entity.getFlowId())
                .map(CimFlow::getFlowCode).orElse(entity.getFlowId());
        flowEventService.record(entity.getFlowId(), flowCode, FlowEventService.T_ASSIGN, action,
                FlowEventService.summary("scope", entity.getScopeType(),
                        "stepId", "".equals(entity.getStepId()) ? null : entity.getStepId(),
                        "assignType", entity.getAssignType(),
                        "assignRef", entity.getAssignRef(), "priority", entity.getPriority()));
    }

    private CimEquipAssign doCreate(String scope, String opCode, String flowId,
                                    String nodeId, AssignReq req) {
        CimEquipAssign entity = new CimEquipAssign();
        entity.setScopeType(scope);
        entity.setOperationCode(opCode == null ? "" : opCode);
        entity.setFlowId(flowId == null ? "" : flowId);
        entity.setStepId(nodeId == null ? "" : nodeId);
        applyFields(entity, req);
        assertContextValid(entity);
        assertNotDuplicate(entity, null);
        return assignRepository.save(entity);
    }

    private void applyFields(CimEquipAssign entity, AssignReq req) {
        String type = req.assignType() == null ? "" : req.assignType().trim();
        String ref = req.assignRef() == null ? "" : req.assignRef().trim();
        if (!VALID_TYPES.contains(type)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.typeInvalid", type);
        }
        if (ref.isBlank()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.refRequired");
        }
        entity.setAssignType(type);
        entity.setAssignRef(ref);
        entity.setChamberRule(req.chamberRule());
        // recipe_rule 守门
        String rule = req.recipeRule() == null || req.recipeRule().isBlank()
                ? CimEquipAssign.RULE_FIXED : req.recipeRule().trim();
        if (!VALID_RECIPE_RULES.contains(rule)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.recipeRuleInvalid", rule);
        }
        entity.setRecipeRule(rule);
        entity.setRecipeRef(req.recipeRef());
        entity.setRecipeMatch(req.recipeMatch());
        if (CimEquipAssign.RULE_FIXED.equals(rule)
                && (req.recipeRef() == null || req.recipeRef().isBlank())) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.fixedRecipeRequired");
        }
        if (CimEquipAssign.RULE_EXACT_MATCH.equals(rule)) {
            try {
                objectMapper.readTree(req.recipeMatch() == null ? "" : req.recipeMatch());
            } catch (Exception e) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.recipeMatchJsonInvalid");
            }
        }
        entity.setPriority(req.priority() == null ? 100 : req.priority());
        entity.setEnabled(req.enabled() == null ? Boolean.TRUE : req.enabled());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        // assign_ref 存在且 enabled（GROUP/EQUIP），CLASS 只查存在
        switch (type) {
            case CimEquipAssign.TYPE_GROUP -> {
                if (toolGroupRepository.findByGroupCodeAndDeletedFalse(ref)
                        .filter(g -> Boolean.TRUE.equals(g.getEnabled())).isEmpty()) {
                    throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.assign.groupNotFound", ref);
                }
            }
            case CimEquipAssign.TYPE_EQUIP -> {
                if (equipmentRepository.findByEquipmentCodeAndDeletedFalse(ref)
                        .filter(e -> Boolean.TRUE.equals(e.getEnabled())).isEmpty()) {
                    throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.assign.equipmentNotFound", ref);
                }
            }
            case CimEquipAssign.TYPE_CLASS -> {
                if (equipmentClassRepository.findByClassCodeAndDeletedFalse(ref).isEmpty()) {
                    throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.assign.classNotFound", ref);
                }
            }
            default -> throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.typeInvalid", type);
        }
    }

    /** scope 与上下文列匹配守门（FLOW 级 step_id 必空串；STEP 级 step_id 必填；PRODUCT 级 product+family 必填）。 */
    private void assertContextValid(CimEquipAssign entity) {
        String scope = entity.getScopeType();
        if (!VALID_SCOPES.contains(scope)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.scopeInvalid", scope);
        }
        switch (scope) {
            case CimEquipAssign.SCOPE_OPERATION -> {
                if (entity.getOperationCode().isBlank()) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.opScopeNeedsOpCode");
                }
                if (!entity.getFlowId().isEmpty() || !entity.getStepId().isEmpty()) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.opScopeContextDirty");
                }
            }
            case CimEquipAssign.SCOPE_FLOW -> {
                if (entity.getFlowId().isBlank() || !entity.getStepId().isEmpty()
                        || !entity.getOperationCode().isEmpty()) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.flowScopeContextDirty");
                }
            }
            case CimEquipAssign.SCOPE_STEP -> {
                if (entity.getFlowId().isBlank() || entity.getStepId().isBlank()
                        || !entity.getOperationCode().isEmpty()) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.stepScopeContextDirty");
                }
            }
            case CimEquipAssign.SCOPE_PRODUCT -> {
                // product_code + family_code 必填；step_id 可空（=整条流程生效）；flow_id/operation_code 空
                if (entity.getProductCode().isBlank() || entity.getFamilyCode().isBlank()) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.productScopeContextDirty");
                }
                if (!entity.getFlowId().isEmpty() || !entity.getOperationCode().isEmpty()) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.productScopeContextDirty");
                }
            }
            default -> throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.assign.scopeInvalid", scope);
        }
    }

    /** uk_ea 重复守门（更新时排除自身）。 */
    private void assertNotDuplicate(CimEquipAssign entity, String selfId) {
        List<CimEquipAssign> same = switch (entity.getScopeType()) {
            case CimEquipAssign.SCOPE_OPERATION -> assignRepository
                    .findByScopeTypeAndOperationCodeAndDeletedFalse(
                            CimEquipAssign.SCOPE_OPERATION, entity.getOperationCode());
            case CimEquipAssign.SCOPE_FLOW -> assignRepository
                    .findByScopeTypeAndFlowIdAndDeletedFalse(
                            CimEquipAssign.SCOPE_FLOW, entity.getFlowId());
            case CimEquipAssign.SCOPE_PRODUCT -> assignRepository
                    .findByScopeTypeAndProductCodeAndFamilyCodeAndDeletedFalse(
                            CimEquipAssign.SCOPE_PRODUCT, entity.getProductCode(), entity.getFamilyCode());
            default -> assignRepository.findByScopeTypeAndFlowIdAndStepIdAndDeletedFalse(
                    CimEquipAssign.SCOPE_STEP, entity.getFlowId(), entity.getStepId());
        };
        boolean dup = same.stream().anyMatch(o -> !o.getId().equals(selfId)
                && o.getStepId().equals(entity.getStepId())
                && o.getAssignType().equals(entity.getAssignType())
                && o.getAssignRef().equals(entity.getAssignRef())
                && (o.getPriority() == null ? 100 : o.getPriority())
                        == (entity.getPriority() == null ? 100 : entity.getPriority())
                // 配方口径属行身差异：同设备同优先级不同配方允许入池（歧义交 validate WARN + 解算 AMBIGUOUS_POOL 硬拦）
                && o.getRecipeRule().equals(entity.getRecipeRule())
                && java.util.Objects.equals(o.getRecipeRef(), entity.getRecipeRef())
                && java.util.Objects.equals(o.getRecipeMatch(), entity.getRecipeMatch()));
        if (dup) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.assign.duplicate",
                    entity.getAssignType() + ":" + entity.getAssignRef());
        }
    }

    /** 四态只读守门：scope=FLOW/STEP 行所属流程须 UNFROZEN（2004）；OPERATION/PRODUCT 行不挂流程状态。 */
    private void requireEditable(CimEquipAssign entity) {
        if (CimEquipAssign.SCOPE_OPERATION.equals(entity.getScopeType())
                || CimEquipAssign.SCOPE_PRODUCT.equals(entity.getScopeType())) {
            return; // L0 随工序模板、L3 随产品域（独立配置，不受流程四态约束）
        }
        CimFlow flow = flowRepository.findById(entity.getFlowId())
                .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.notFound", entity.getFlowId()));
        requireUnfrozen(flow);
    }

    private CimEquipAssign requireAssign(String id) {
        return assignRepository.findById(id)
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.assign.notFound", id));
    }

    private CimOperation requireOperation(String opCode) {
        return operationRepository.findByOpCodeAndDeletedFalse(opCode)
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.opRefNotFound", opCode));
    }

    private void requireEditableFlow(String flowId) {
        CimFlow flow = flowRepository.findById(flowId)
                .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.notFound", flowId));
        requireUnfrozen(flow);
    }

    private void requireStep(String flowId, String nodeId) {
        if (stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(flowId).stream()
                .noneMatch(s -> s.getNodeId().equals(nodeId))) {
            throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.assign.stepNotFound", nodeId);
        }
    }

    static void requireUnfrozen(CimFlow flow) {
        if (!CimFlow.STATUS_UNFROZEN.equals(flow.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.overrideReadOnly", flow.getStatus());
        }
    }
}
