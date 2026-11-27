package com.cim.mds.server.flow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * 流程约束校验服务（ICM 表驱动规则，rule_code 可启停）。
 *
 * <p><b>分工</b>：图结构规则（START/END/可达/环/SPLIT MERGE/Q-time 可达）仍在
 * {@link FlowAdminService#validate}（规则 1-8）；本服务负责 <b>ICM 约束类规则</b>
 * （指派/KV/配方/光罩/污染/工序引用）——全部消费 {@link ContextResolver} 解析值，
 * 不允许各处各算。编排：FlowAdminService.validateFull 把结构问题传入本服务，
 * 合并落库 validate_state + cim_flow_validate_result（REQUIRES_NEW）。</p>
 *
 * <p>规则清单（flow-modeling/20-our-design.md §5）：ERROR≥1 → INVALID；0 ERROR 有
 * WARN → VALID。规则以注册表形式维护（P2 接控制开关做运行时启停）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowValidateService {

    private final CimEquipAssignRepository assignRepository;
    private final CimConstraintAttrRepository attrRepository;
    private final CimParamDefRepository paramDefRepository;
    private final CimOperationRepository operationRepository;
    private final CimFlowStepRepository stepRepository;
    private final CimToolGroupRepository toolGroupRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final CimEquipmentClassRepository equipmentClassRepository;
    private final ObjectMapper objectMapper;

    /**
     * 必填参数强校验灰度开关（P2，roadmap）：false（默认）= PARAM_REQUIRED_MISSING 报 WARN
     * （灰度观察期）；true = 升级为 ERROR（直接拉低 validate_state 阻断激活）。
     */
    @org.springframework.beans.factory.annotation.Value("${cim.flow.required-strict:false}")
    private boolean requiredStrict;

    /** 规则码（前端校验报告/启用开关共用）。 */
    public static final String R_FLOW_NODE_REQUIRED = "FLOW_NODE_REQUIRED";
    public static final String R_ASSIGN_REQUIRED = "ASSIGN_REQUIRED";
    public static final String R_ASSIGN_REF_VALID = "ASSIGN_REF_VALID";
    public static final String R_ASSIGN_MATCHES_EQUIP_TYPE = "ASSIGN_MATCHES_EQUIP_TYPE";
    public static final String R_OP_REF_EXISTS = "OP_REF_EXISTS";
    public static final String R_OP_REF_STALE = "OP_REF_STALE";
    public static final String R_RECIPE_RESOLVED = "RECIPE_RESOLVED";
    public static final String R_RECIPE_CONSISTENT = "RECIPE_CONSISTENT";
    /** V29：候选池同优先级配方口径不一致（解算歧义预警；运行时 resolve 已硬拦 AMBIGUOUS_POOL）。 */
    public static final String R_RECIPE_POOL_AMBIGUOUS = "RECIPE_POOL_AMBIGUOUS";
    public static final String R_RETICLE_RESOLVED = "RETICLE_RESOLVED";
    public static final String R_CONTAM_VALID = "CONTAM_VALID";
    public static final String R_STRUCTURE_VALID = "STRUCTURE_VALID";
    public static final String R_PARAM_KEY_DEFINED = "PARAM_KEY_DEFINED";
    public static final String R_PARAM_REQUIRED_MISSING = "PARAM_REQUIRED_MISSING";
    public static final String R_OVERRIDE_SAME_AS_DEFAULT = "OVERRIDE_SAME_AS_DEFAULT";

    /** 逐条明细（落库用）。 */
    public record Detail(String stepNodeId, String ruleCode, String severity, String message) {
    }

    /**
     * 跑全量 ICM 约束规则（消费 ContextResolver 解析值）。
     *
     * @param flow            流程主档
     * @param structureIssues 图结构规则产出的问题清单（FlowAdminService.validateGraph），
     *                        每条并入为 STRUCTURE_VALID ERROR 行
     */
    @Transactional(readOnly = true)
    public List<Detail> runRules(CimFlow flow, List<String> structureIssues) {
        List<Detail> out = new ArrayList<>();
        for (String issue : structureIssues) {
            out.add(new Detail(null, R_STRUCTURE_VALID,
                    CimFlowValidateResult.SEVERITY_ERROR, issue));
        }
        List<CimFlowStep> steps = stepListOf(flow);
        if (steps.isEmpty()) {
            return out;
        }
        ContextResolver.ResolveCtx ctx = buildCtx(flow, steps);
        List<ContextResolver.NodeEffective> nodes = ContextResolver.resolveAll(ctx);

        Set<String> nodeIds = new HashSet<>();
        for (CimFlowStep s : steps) {
            nodeIds.add(s.getNodeId());
        }
        Map<String, CimFlowStep> byNode = new java.util.HashMap<>();
        steps.forEach(s -> byNode.put(s.getNodeId(), s));

        for (ContextResolver.NodeEffective n : nodes) {
            CimFlowStep s = byNode.get(n.nodeId());
            boolean business = CimFlowStep.TYPE_PROCESS.equals(n.stepType())
                    || CimFlowStep.TYPE_INSPECT.equals(n.stepType())
                    || CimFlowStep.TYPE_REWORK.equals(n.stepType());
            // OP_REF_EXISTS：引用存在且 ACTIVE（现状口径：只有 ACTIVE 工序可被节点引用）
            if (n.operationCode() != null && !n.operationCode().isBlank()) {
                CimOperation op = n.opStatus() == null ? null
                        : operationRepository.findByOpCodeAndDeletedFalse(n.operationCode()).orElse(null);
                if (op == null) {
                    out.add(new Detail(n.nodeId(), R_OP_REF_EXISTS,
                            CimFlowValidateResult.SEVERITY_ERROR,
                            n.stepCode() + " 引用的工序不存在: " + n.operationCode()));
                } else if (!CimOperation.STATUS_ACTIVE.equals(op.getStatus())) {
                    out.add(new Detail(n.nodeId(), R_OP_REF_EXISTS,
                            CimFlowValidateResult.SEVERITY_ERROR,
                            n.stepCode() + " 引用的工序不是 ACTIVE 版本: " + n.operationCode()
                                    + "（" + op.getStatus() + "）"));
                }
            }
            // OP_REF_STALE：族内有更新 ACTIVE 版本（WARN 提示「刷新工序引用」）
            if (n.opRefStale()) {
                out.add(new Detail(n.nodeId(), R_OP_REF_STALE,
                        CimFlowValidateResult.SEVERITY_WARN,
                        n.stepCode() + " 引用的工序 " + n.operationCode()
                                + " 不是族内最新 ACTIVE 版本，可执行「刷新工序引用」"));
            }
            // ASSIGN_REQUIRED：业务节点解析后候选池 ≥1 行（含继承）
            if (business && n.pool().isEmpty()) {
                out.add(new Detail(n.nodeId(), R_ASSIGN_REQUIRED,
                        CimFlowValidateResult.SEVERITY_ERROR,
                        n.stepCode() + " 无机台指派（沿 ContextChain 三层均未配置）"));
            }
            // ASSIGN_REF_VALID / ASSIGN_MATCHES_EQUIP_TYPE / RECIPE_RESOLVED：逐候选池行
            for (CimEquipAssign a : n.pool()) {
                checkAssignRow(n, s, a, out);
            }
            // RECIPE_POOL_AMBIGUOUS（V29 WARN）：同优先级多行且配方口径（rule+ref+match）不一致
            // ——全自动解算要求唯一结果，运行时 resolve 会以 AMBIGUOUS_POOL 硬拦；这里提前暴露给建模期
            if (business && n.pool().size() > 1) {
                java.util.Map<Integer, java.util.Set<String>> byPriority = new java.util.LinkedHashMap<>();
                java.util.Map<Integer, java.util.List<CimEquipAssign>> rowsByPriority = new java.util.LinkedHashMap<>();
                for (CimEquipAssign a : n.pool()) {
                    int pr = a.getPriority() == null ? 100 : a.getPriority();
                    String rule = a.getRecipeRule() == null ? CimEquipAssign.RULE_FIXED : a.getRecipeRule();
                    String key = rule + "|" + (a.getRecipeRef() == null ? "" : a.getRecipeRef())
                            + "|" + (a.getRecipeMatch() == null ? "" : a.getRecipeMatch());
                    byPriority.computeIfAbsent(pr, k -> new java.util.LinkedHashSet<>()).add(key);
                    rowsByPriority.computeIfAbsent(pr, k -> new java.util.ArrayList<>()).add(a);
                }
                for (var e : byPriority.entrySet()) {
                    if (e.getValue().size() > 1) {
                        out.add(new Detail(n.nodeId(), R_RECIPE_POOL_AMBIGUOUS,
                                CimFlowValidateResult.SEVERITY_WARN,
                                n.stepCode() + " 候选池优先级 " + e.getKey() + " 上有 "
                                        + rowsByPriority.get(e.getKey()).size()
                                        + " 条配方口径不一致的指派行，解算将报 AMBIGUOUS_POOL"));
                    }
                }
            }
            // RETICLE_RESOLVED：LITHO 类节点解析后光罩必填
            String equipType = n.equipType().value();
            if (equipType != null && equipType.contains("LITHO")
                    && (n.reticleRef().value() == null || n.reticleRef().value().isBlank())) {
                out.add(new Detail(n.nodeId(), R_RETICLE_RESOLVED,
                        CimFlowValidateResult.SEVERITY_ERROR,
                        n.stepCode() + "（光刻类节点）解析后光罩引用为空"));
            }
            // CONTAM_VALID：forbidden 不含自身 contam_level
            String level = n.contamLevel().value();
            String forbidden = n.contamForbidden().value();
            if (level != null && forbidden != null && !forbidden.isBlank()) {
                for (String f : forbidden.split("[,，]")) {
                    if (f.trim().equals(level)) {
                        out.add(new Detail(n.nodeId(), R_CONTAM_VALID,
                                CimFlowValidateResult.SEVERITY_ERROR,
                                n.stepCode() + " 污染级 " + level + " 在自身禁止清单内"));
                    }
                }
            }
            // KV：PARAM_KEY_DEFINED（WARN）/ PARAM_REQUIRED_MISSING（WARN）
            Set<String> seenKeys = new HashSet<>();
            for (ContextResolver.AttrEffective kv : n.attrs().values()) {
                seenKeys.add(kv.attrKey());
                if (kv.def() == null || !Boolean.TRUE.equals(kv.def().getEnabled())) {
                    out.add(new Detail(n.nodeId(), R_PARAM_KEY_DEFINED,
                            CimFlowValidateResult.SEVERITY_WARN,
                            n.stepCode() + " 的参数 " + kv.attrKey() + " 未在参数定义注册或已停用"));
                }
            }
            // required=true 且全链无值（ContextResolver 已把未落库的注册 def 也并入 attrs，
            // value=null = 沿链任何一层都没落值——直接看解析值，不能用 key 存在性判断）
            for (CimParamDef def : paramDefRepository.findByDeletedFalseOrderBySortNoAsc()) {
                if (Boolean.TRUE.equals(def.getRequired()) && def.getScopes() != null
                        && def.getScopes().contains(CimEquipAssign.SCOPE_STEP)) {
                    ContextResolver.AttrEffective kv = n.attrs().get(def.getAttrKey());
                    boolean hasValue = kv != null && kv.value() != null && !kv.value().isBlank();
                    if (!hasValue) {
                        out.add(new Detail(n.nodeId(), R_PARAM_REQUIRED_MISSING,
                                requiredStrict ? CimFlowValidateResult.SEVERITY_ERROR
                                        : CimFlowValidateResult.SEVERITY_WARN,
                                n.stepCode() + " 必填参数 " + def.getAttrKey() + " 全链无值"));
                    }
                }
            }
            // OVERRIDE_SAME_AS_DEFAULT（列型）：step 覆盖 == op 默认 → WARN
            if (s.getOperationCode() != null) {
                CimOperation op = operationRepository
                        .findByOpCodeAndDeletedFalse(s.getOperationCode()).orElse(null);
                if (op != null) {
                    checkColumnOverride(n, s, op, out);
                }
            }
        }
        // FLOW_NODE_REQUIRED：至少 1 个业务节点
        boolean anyBusiness = nodes.stream().anyMatch(n ->
                CimFlowStep.TYPE_PROCESS.equals(n.stepType())
                        || CimFlowStep.TYPE_INSPECT.equals(n.stepType())
                        || CimFlowStep.TYPE_REWORK.equals(n.stepType()));
        if (!anyBusiness) {
            out.add(new Detail(null, R_FLOW_NODE_REQUIRED,
                    CimFlowValidateResult.SEVERITY_ERROR,
                    "流程没有任何业务工序节点（PROCESS/INSPECT/REWORK）"));
        }
        return out;
    }

    /** 单条候选池行守门：引用存在 + 机型一致 + 配方规则。 */
    private void checkAssignRow(ContextResolver.NodeEffective n, CimFlowStep s,
                                CimEquipAssign a, List<Detail> out) {
        String where = n.stepCode() + " 的候选池行 [" + a.getAssignType() + ":" + a.getAssignRef() + "]";
        // ASSIGN_REF_VALID
        boolean refOk = switch (a.getAssignType()) {
            case CimEquipAssign.TYPE_GROUP -> toolGroupRepository
                    .findByGroupCodeAndDeletedFalse(a.getAssignRef())
                    .map(g -> Boolean.TRUE.equals(g.getEnabled())).orElse(false);
            case CimEquipAssign.TYPE_EQUIP -> equipmentRepository
                    .findByEquipmentCodeAndDeletedFalse(a.getAssignRef())
                    .map(e -> Boolean.TRUE.equals(e.getEnabled())).orElse(false);
            case CimEquipAssign.TYPE_CLASS -> equipmentClassRepository
                    .findByClassCodeAndDeletedFalse(a.getAssignRef()).isPresent();
            default -> false;
        };
        if (!refOk) {
            out.add(new Detail(n.nodeId(), R_ASSIGN_REF_VALID,
                    CimFlowValidateResult.SEVERITY_ERROR,
                    where + " 引用不存在或已停用"));
        }
        // ASSIGN_MATCHES_EQUIP_TYPE：CLASS/EQUIP 行的机型须与节点资格门一致
        String gate = n.equipType().value();
        if (gate != null && !gate.isBlank()) {
            String rowClass = null;
            if (CimEquipAssign.TYPE_CLASS.equals(a.getAssignType())) {
                rowClass = a.getAssignRef();
            } else if (CimEquipAssign.TYPE_EQUIP.equals(a.getAssignType())) {
                rowClass = equipmentRepository.findByEquipmentCodeAndDeletedFalse(a.getAssignRef())
                        .map(CimEquipment::getClassCode).orElse(null);
            }
            if (rowClass != null && !rowClass.equals(gate)) {
                out.add(new Detail(n.nodeId(), R_ASSIGN_MATCHES_EQUIP_TYPE,
                        CimFlowValidateResult.SEVERITY_ERROR,
                        where + " 机型 " + rowClass + " 与节点资格门 " + gate + " 不一致"));
            }
        }
        // RECIPE_RESOLVED：FIXED → ref 必填；EXACT_MATCH → JSON 合法
        String rule = a.getRecipeRule() == null ? CimEquipAssign.RULE_FIXED : a.getRecipeRule();
        if (CimEquipAssign.RULE_FIXED.equals(rule)
                && (a.getRecipeRef() == null || a.getRecipeRef().isBlank())) {
            out.add(new Detail(n.nodeId(), R_RECIPE_RESOLVED,
                    CimFlowValidateResult.SEVERITY_ERROR,
                    where + " recipe_rule=FIXED 但未填 recipe_ref"));
        }
        // RECIPE_CONSISTENT（WARN）：FIXED 行配方与节点生效 recipeRef 不一致
        // （对应行业「派工预检 qualified recipe」：节点主配方与机台候选锁定配方应可对上）
        String effRecipe = n.recipeRef().value();
        if (CimEquipAssign.RULE_FIXED.equals(rule)
                && a.getRecipeRef() != null && !a.getRecipeRef().isBlank()
                && effRecipe != null && !effRecipe.isBlank()
                && !a.getRecipeRef().equals(effRecipe)) {
            out.add(new Detail(n.nodeId(), R_RECIPE_CONSISTENT,
                    CimFlowValidateResult.SEVERITY_WARN,
                    where + " 固定配方 " + a.getRecipeRef()
                            + " 与节点生效配方 " + effRecipe + " 不一致（派工选中该候选时以固定配方执行）"));
        }
        if (CimEquipAssign.RULE_EXACT_MATCH.equals(rule)) {
            try {
                objectMapper.readTree(a.getRecipeMatch() == null ? "" : a.getRecipeMatch());
            } catch (Exception e) {
                out.add(new Detail(n.nodeId(), R_RECIPE_RESOLVED,
                        CimFlowValidateResult.SEVERITY_ERROR,
                        where + " recipe_match 不是合法 JSON"));
            }
        }
    }

    /** 列型冗余覆盖检测（step 覆盖值与工序默认相同 → WARN）。 */
    private void checkColumnOverride(ContextResolver.NodeEffective n, CimFlowStep s,
                                     CimOperation op, List<Detail> out) {
        record Col(String name, String stepV, String opV) {
        }
        List<Col> cols = new ArrayList<>();
        if (s.getRecipeRef() != null && s.getRecipeRef().equals(op.getRecipeRef())) {
            cols.add(new Col("recipe_ref", s.getRecipeRef(), op.getRecipeRef()));
        }
        if (s.getEquipType() != null && s.getEquipType().equals(op.getEquipType())) {
            cols.add(new Col("equip_type", s.getEquipType(), op.getEquipType()));
        }
        if (s.getQtimeMin() != null && s.getQtimeMin().equals(op.getQtimeMin())) {
            cols.add(new Col("qtime_min", String.valueOf(s.getQtimeMin()), String.valueOf(op.getQtimeMin())));
        }
        if (s.getQtimeMax() != null && s.getQtimeMax().equals(op.getQtimeMax())) {
            cols.add(new Col("qtime_max", String.valueOf(s.getQtimeMax()), String.valueOf(op.getQtimeMax())));
        }
        if (s.getReticleRef() != null && s.getReticleRef().equals(op.getReticleRef())) {
            cols.add(new Col("reticle_ref", s.getReticleRef(), op.getReticleRef()));
        }
        if (!cols.isEmpty()) {
            out.add(new Detail(n.nodeId(), R_OVERRIDE_SAME_AS_DEFAULT,
                    CimFlowValidateResult.SEVERITY_WARN,
                    n.stepCode() + " 存在与工序默认相同的冗余覆盖: "
                            + cols.stream().map(Col::name).reduce((a, b) -> a + "/" + b).orElse("")));
        }
    }

    private List<CimFlowStep> stepListOf(CimFlow flow) {
        return stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(flow.getId());
    }

    private ContextResolver.ResolveCtx buildCtx(CimFlow flow, List<CimFlowStep> steps) {
        String flowId = flow.getId();
        Set<String> opCodes = new HashSet<>();
        steps.forEach(s -> {
            if (s.getOperationCode() != null && !s.getOperationCode().isBlank()) {
                opCodes.add(s.getOperationCode());
            }
        });
        Map<String, CimOperation> operations = new java.util.HashMap<>();
        for (String code : opCodes) {
            operationRepository.findByOpCodeAndDeletedFalse(code).ifPresent(op -> operations.put(code, op));
        }
        // 族内 ACTIVE 集合（OP_REF_STALE 判据需要全族）
        Set<String> families = new HashSet<>();
        operations.values().forEach(op -> {
            if (op.getOpFamilyCode() != null) {
                families.add(op.getOpFamilyCode());
            }
        });
        for (String family : families) {
            operationRepository.findByOpFamilyCodeAndDeletedFalseAndStatus(
                    family, CimOperation.STATUS_ACTIVE)
                    .ifPresent(op -> operations.put(op.getOpCode(), op));
        }
        List<CimEquipAssign> assigns = new ArrayList<>();
        assigns.addAll(assignRepository.findByScopeTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_FLOW, flowId));
        assigns.addAll(assignRepository.findByScopeTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_STEP, flowId));
        for (String code : opCodes) {
            assigns.addAll(assignRepository.findByScopeTypeAndOperationCodeAndDeletedFalse(
                    CimEquipAssign.SCOPE_OPERATION, code));
        }
        List<CimConstraintAttr> attrs = new ArrayList<>();
        attrs.addAll(attrRepository.findByContextTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_FLOW, flowId));
        attrs.addAll(attrRepository.findByContextTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_STEP, flowId));
        for (String code : opCodes) {
            attrs.addAll(attrRepository.findByContextTypeAndOperationCodeAndDeletedFalse(
                    CimEquipAssign.SCOPE_OPERATION, code));
        }
        Map<String, CimParamDef> defs = new java.util.HashMap<>();
        paramDefRepository.findByDeletedFalseOrderBySortNoAsc().forEach(d -> defs.put(d.getAttrKey(), d));
        return new ContextResolver.ResolveCtx(flow, steps, operations, assigns, attrs, defs);
    }

    /** 校验态归集（供编排方落库）：ERROR≥1 → INVALID；否则 VALID（含 0 ERROR 0 WARN）。 */
    public static String stateOf(List<Detail> details) {
        boolean anyError = details.stream()
                .anyMatch(d -> CimFlowValidateResult.SEVERITY_ERROR.equals(d.severity()));
        return anyError ? CimFlow.VS_INVALID : CimFlow.VS_VALID;
    }

    /** 明细计数摘要（validate_msg 用）。 */
    public static String summaryOf(List<Detail> details) {
        long errors = details.stream()
                .filter(d -> CimFlowValidateResult.SEVERITY_ERROR.equals(d.severity())).count();
        long warns = details.stream()
                .filter(d -> CimFlowValidateResult.SEVERITY_WARN.equals(d.severity())).count();
        return errors + " ERROR / " + warns + " WARN";
    }

    /** 供 Controller 抛 2004 用（activate 前置闸）。 */
    public BizException invalidException(List<Detail> details) {
        return BizException.of(BizCode.PARAM_INVALID,
                "mds.biz.flow.activateRequiresValid", summaryOf(details));
    }
}
