package com.cim.mds.server.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程独立规则（V15）服务——CRUD + 守门 + 内置模板 + evaluate 契约。
 *
 * <p><b>锚定口径</b>：规则挂「版本族 family_code + version_no」，通过任一版本
 * （flowId）定位到族锚点；仅 <b>UNFROZEN</b> 版本可维护规则（与画布编辑口径
 * 一致——FROZEN/ACTIVE 版本规则随版本定稿只读，仅供 evaluate 消费）。</p>
 *
 * <p><b>评估口径</b>：enabled 规则按 join_point 过滤后按 priority 升序评估；
 * 条件空 = 恒真；HOLD/SCRAP 终态短路，ROUTE_OVERRIDE/INSERT_SEQUENCE 取优先级
 * 最高的命中；无命中 = 隐式放行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowRuleService {

    private final CimFlowRuleRepository ruleRepository;
    private final CimFlowRepository flowRepository;
    private final CimFlowStepRepository stepRepository;
    private final CimFlowEdgeRepository edgeRepository;
    private final CimFlowQtimeRepository qtimeRepository;

    // ---------------------------------------------------------------- 常量

    /** 合法挂接点。 */
    private static final Set<String> VALID_JOIN_POINTS = Set.of(
            CimFlowRule.JOIN_BEFORE_STEP_ENTER,
            CimFlowRule.JOIN_AFTER_STEP_COMPLETE,
            CimFlowRule.JOIN_BEFORE_DISPATCH);

    /** 合法处置。 */
    private static final Set<String> VALID_EFFECTS = Set.of(
            CimFlowRule.EFFECT_ALLOW, CimFlowRule.EFFECT_HOLD, CimFlowRule.EFFECT_SCRAP,
            CimFlowRule.EFFECT_ROUTE_OVERRIDE, CimFlowRule.EFFECT_INSERT_SEQUENCE);

    /** 合法内置模板。 */
    private static final Set<String> VALID_TEMPLATES = Set.of(
            CimFlowRule.TEMPLATE_QTIME_OVER_HOLD, CimFlowRule.TEMPLATE_BIN_ROUTE);

    /** 合法边语义（与画布连线四语义一致）。 */
    private static final Set<String> VALID_EDGE_TYPES = Set.of(
            CimFlowEdge.TYPE_MAIN, CimFlowEdge.TYPE_PASS,
            CimFlowEdge.TYPE_FAIL, CimFlowEdge.TYPE_REWORK);

    /** 优先级下限。 */
    private static final int MIN_PRIORITY = 0;

    // ---------------------------------------------------------------- 视图 / 请求

    /** 规则视图。 */
    public record RuleView(String id, String flowId, String familyCode, Integer versionNo,
                           String ruleCode, String ruleName, String joinPoint,
                           String stepCode, String nodeId, String edgeType,
                           String conditionExpr, String effect, String effectParam,
                           Integer priority, Boolean enabled, String templateKey) {
    }

    /** 规则新建/更新请求。stepCode/nodeId/edgeType/conditionExpr/effectParam 可空。 */
    public record RuleReq(String ruleCode, String ruleName, String joinPoint,
                          String stepCode, String nodeId, String edgeType,
                          String conditionExpr, String effect, String effectParam,
                          Integer priority, Boolean enabled) {
    }

    /** 评估请求：vars 为运行期变量（bin/qty/elapsedMinutes 等，MES/MCS 侧传入）。 */
    public record EvaluateReq(String flowId, String joinPoint, String stepCode,
                              String nodeId, String edgeType, Map<String, Object> vars) {
    }

    /** 单条命中。 */
    public record RuleHit(String ruleCode, String ruleName, String effect, String effectParam,
                          Integer priority, String conditionExpr, List<String> unknownVars) {
    }

    /** 评估决策：无命中 = ALLOW（隐式放行）。 */
    public record Decision(String action, String ruleCode, String param) {
    }

    /** 评估结果。 */
    public record EvaluateResult(List<RuleHit> matched, Decision decision) {
    }

    /** 模板套用请求。 */
    public record TemplateReq(String flowId, String templateKey) {
    }

    // ---------------------------------------------------------------- 查询

    @Transactional(readOnly = true)
    public List<RuleView> listByFlow(String flowId) {
        CimFlow flow = requireFlow(flowId);
        return findByFamily(flow.getFamilyCode(), flow.getVersionNo());
    }

    // ---------------------------------------------------------------- CRUD

    @Transactional
    public RuleView create(String flowId, RuleReq req) {
        CimFlow flow = requireEditableFlow(flowId);
        String ruleCode = requireText(req.ruleCode(), "ruleCode");
        if (ruleRepository.existsByFamilyCodeAndVersionNoAndRuleCodeAndDeletedFalse(
                flow.getFamilyCode(), flow.getVersionNo(), ruleCode)) {
            throw new BizException(BizCode.DATA_DUPLICATE, "规则编码已存在: " + ruleCode);
        }
        CimFlowRule rule = new CimFlowRule();
        applyFields(rule, flow, req);
        rule.setRuleCode(ruleCode);
        return toView(ruleRepository.save(rule), flow);
    }

    @Transactional
    public RuleView update(String id, RuleReq req) {
        CimFlowRule rule = requireRule(id);
        CimFlow flow = requireEditableFlowByFamily(rule.getFamilyCode(), rule.getVersionNo());
        String newCode = requireText(req.ruleCode(), "ruleCode");
        if (!newCode.equals(rule.getRuleCode())
                && ruleRepository.existsByFamilyCodeAndVersionNoAndRuleCodeAndDeletedFalse(
                        rule.getFamilyCode(), rule.getVersionNo(), newCode)) {
            throw new BizException(BizCode.DATA_DUPLICATE, "规则编码已存在: " + newCode);
        }
        applyFields(rule, flow, req);
        rule.setRuleCode(newCode);
        return toView(rule, flow);
    }

    /** 删除（软删；仅 UNFROZEN 版本）。 */
    @Transactional
    public void delete(String id) {
        CimFlowRule rule = requireRule(id);
        requireEditableFlowByFamily(rule.getFamilyCode(), rule.getVersionNo());
        rule.setDeleted(true);
        ruleRepository.save(rule);
    }

    // ---------------------------------------------------------------- 内置模板

    /**
     * 套用内置模板，按当前版本的解析产物生成规则条目：
     * <ul>
     *   <li>{@code QTIME_OVER_HOLD}：每条跨站 Q-time 约束生成一条 BEFORE_DISPATCH
     *       HOLD 规则（条件 elapsedMinutes &gt; 上限，参数 = 原约束动作语义）。</li>
     *   <li>{@code BIN_ROUTE}：每条带条件表达式的边生成一条 AFTER_STEP_COMPLETE
     *       ROUTE_OVERRIDE 规则（条件/目标取自边，模板生成的条目 rule_code 前缀
     *       {@code TPL-}，同族同版唯一冲突时序号后移）。</li>
     * </ul>
     */
    @Transactional
    public List<RuleView> applyTemplate(TemplateReq req) {
        String templateKey = requireText(req.templateKey(), "templateKey");
        if (!VALID_TEMPLATES.contains(templateKey)) {
            throw new BizException(BizCode.PARAM_INVALID, "未知模板: " + templateKey);
        }
        CimFlow flow = requireEditableFlow(req.flowId());
        List<RuleView> created = new ArrayList<>();
        if (CimFlowRule.TEMPLATE_QTIME_OVER_HOLD.equals(templateKey)) {
            created.addAll(applyQtimeTemplate(flow));
        } else {
            created.addAll(applyBinRouteTemplate(flow));
        }
        if (created.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID,
                    CimFlowRule.TEMPLATE_QTIME_OVER_HOLD.equals(templateKey)
                            ? "当前版本没有跨站 Q-time 定义可套用"
                            : "当前版本没有带条件的连线可套用");
        }
        return created;
    }

    private List<RuleView> applyQtimeTemplate(CimFlow flow) {
        List<RuleView> created = new ArrayList<>();
        int seq = 1;
        for (CimFlowQtime q : qtimeRepository.findByFlowIdAndDeletedFalse(flow.getId())) {
            String code = nextTemplateCode(flow, "QH", seq);
            CimFlowRule rule = new CimFlowRule();
            rule.setFamilyCode(flow.getFamilyCode());
            rule.setVersionNo(flow.getVersionNo());
            rule.setRuleCode(code);
            rule.setRuleName("Q-time 超限挂起 " + q.getFromNodeId() + "→" + q.getToNodeId());
            rule.setJoinPoint(CimFlowRule.JOIN_BEFORE_DISPATCH);
            rule.setNodeId(q.getFromNodeId());
            rule.setConditionExpr("elapsedMinutes > " + q.getMaxMinutes());
            rule.setEffect(CimFlowRule.EFFECT_HOLD);
            rule.setEffectParam("跨站 Q-time 超限（" + q.getFromNodeId() + "→"
                    + q.getToNodeId() + " ≤" + q.getMaxMinutes() + " 分钟），暂停等人工释放");
            rule.setPriority(50);
            rule.setEnabled(Boolean.TRUE);
            rule.setTemplateKey(CimFlowRule.TEMPLATE_QTIME_OVER_HOLD);
            created.add(toView(ruleRepository.save(rule), flow));
            seq++;
        }
        return created;
    }

    private List<RuleView> applyBinRouteTemplate(CimFlow flow) {
        List<RuleView> created = new ArrayList<>();
        int seq = 1;
        for (CimFlowEdge e : edgeRepository.findByFlowIdAndDeletedFalse(flow.getId())) {
            String condition = e.getConditionExpr();
            if (condition == null || condition.isBlank()) {
                continue;
            }
            FlowRuleConditionEvaluator.validate(condition);
            String code = nextTemplateCode(flow, "BR", seq);
            CimFlowRule rule = new CimFlowRule();
            rule.setFamilyCode(flow.getFamilyCode());
            rule.setVersionNo(flow.getVersionNo());
            rule.setRuleCode(code);
            rule.setRuleName("Bin 条件选路 " + condition);
            rule.setJoinPoint(CimFlowRule.JOIN_AFTER_STEP_COMPLETE);
            rule.setNodeId(e.getFromStepId());
            // edgeType 不设——改派规则本身决定路由，锚边语义会在评估时把规则过滤掉
            rule.setConditionExpr(condition);
            rule.setEffect(CimFlowRule.EFFECT_ROUTE_OVERRIDE);
            rule.setEffectParam(e.getToStepId());
            rule.setPriority(60);
            rule.setEnabled(Boolean.TRUE);
            rule.setTemplateKey(CimFlowRule.TEMPLATE_BIN_ROUTE);
            created.add(toView(ruleRepository.save(rule), flow));
            seq++;
        }
        return created;
    }

    /** 模板规则编码：TPL 前缀 + 序号，唯一冲突时顺延。 */
    private String nextTemplateCode(CimFlow flow, String prefix, int startSeq) {
        int seq = startSeq;
        while (ruleRepository.existsByFamilyCodeAndVersionNoAndRuleCodeAndDeletedFalse(
                flow.getFamilyCode(), flow.getVersionNo(), "TPL-" + prefix + "-" + seq)) {
            seq++;
        }
        return "TPL-" + prefix + "-" + seq;
    }

    // ---------------------------------------------------------------- evaluate 契约

    /**
     * 评估：取 (family, version) 下 enabled 且 join_point 匹配的规则按 priority
     * 升序评估；规则自身的 step/node/edge 锚点与请求上下文不一致时跳过。
     */
    @Transactional(readOnly = true)
    public EvaluateResult evaluate(EvaluateReq req) {
        String joinPoint = requireText(req.joinPoint(), "joinPoint");
        CimFlow flow = requireFlow(req.flowId());
        Map<String, Object> vars = req.vars() == null ? Map.of() : req.vars();

        List<RuleHit> matched = new ArrayList<>();
        Decision terminal = null;
        Decision routing = null;
        for (CimFlowRule rule : ruleRepository
                .findByFamilyCodeAndVersionNoAndDeletedFalseOrderByPriorityAscRuleCodeAsc(
                        flow.getFamilyCode(), flow.getVersionNo())) {
            if (!Boolean.TRUE.equals(rule.getEnabled())
                    || !joinPoint.equals(rule.getJoinPoint())) {
                continue;
            }
            if (!anchorMatches(rule.getStepCode(), req.stepCode())
                    || !anchorMatches(rule.getNodeId(), req.nodeId())
                    || !anchorMatches(rule.getEdgeType(), req.edgeType())) {
                continue;
            }
            FlowRuleConditionEvaluator.Result result =
                    FlowRuleConditionEvaluator.evaluate(rule.getConditionExpr(), vars);
            if (!result.matched()) {
                continue;
            }
            matched.add(new RuleHit(rule.getRuleCode(), rule.getRuleName(), rule.getEffect(),
                    rule.getEffectParam(), rule.getPriority(), rule.getConditionExpr(),
                    result.unknownVars()));
            if (isTerminal(rule.getEffect()) && terminal == null) {
                terminal = new Decision(rule.getEffect(), rule.getRuleCode(), rule.getEffectParam());
            }
            if (routing == null && (CimFlowRule.EFFECT_ROUTE_OVERRIDE.equals(rule.getEffect())
                    || CimFlowRule.EFFECT_INSERT_SEQUENCE.equals(rule.getEffect()))) {
                routing = new Decision(
                        CimFlowRule.EFFECT_ROUTE_OVERRIDE.equals(rule.getEffect())
                                ? "ROUTE" : "INSERT",
                        rule.getRuleCode(), rule.getEffectParam());
            }
            if (terminal != null) {
                break; // HOLD/SCRAP 终态短路
            }
        }
        Decision decision = terminal != null ? terminal
                : (routing != null ? routing : new Decision("ALLOW", null, null));
        return new EvaluateResult(matched, decision);
    }

    private static boolean isTerminal(String effect) {
        return CimFlowRule.EFFECT_HOLD.equals(effect) || CimFlowRule.EFFECT_SCRAP.equals(effect);
    }

    /** 规则锚点空 = 不限；非空则须与请求上下文一致。 */
    private static boolean anchorMatches(String anchor, String actual) {
        return anchor == null || anchor.isBlank() || anchor.equals(actual);
    }

    // ---------------------------------------------------------------- 内部

    private void applyFields(CimFlowRule rule, CimFlow flow, RuleReq req) {
        String joinPoint = requireText(req.joinPoint(), "joinPoint");
        if (!VALID_JOIN_POINTS.contains(joinPoint)) {
            throw new BizException(BizCode.PARAM_INVALID, "非法挂接点: " + joinPoint);
        }
        String effect = requireText(req.effect(), "effect");
        if (!VALID_EFFECTS.contains(effect)) {
            throw new BizException(BizCode.PARAM_INVALID, "非法处置: " + effect);
        }
        String edgeType = blankToNull(req.edgeType());
        if (edgeType != null && !VALID_EDGE_TYPES.contains(edgeType)) {
            throw new BizException(BizCode.PARAM_INVALID, "非法边语义: " + edgeType);
        }
        if (req.priority() != null && req.priority() < MIN_PRIORITY) {
            throw new BizException(BizCode.PARAM_INVALID, "优先级不能为负数");
        }
        String condition = blankToNull(req.conditionExpr());
        try {
            FlowRuleConditionEvaluator.validate(condition);
        } catch (IllegalArgumentException e) {
            throw new BizException(BizCode.PARAM_INVALID, "条件表达式语法错误: " + e.getMessage());
        }
        // 步骤/节点锚点须存在于当前版本；ROUTE/INSERT 的参数须指向真实目标
        String stepCode = blankToNull(req.stepCode());
        if (stepCode != null && stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(flow.getId())
                .stream().noneMatch(s -> stepCode.equals(s.getStepCode()))) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "步骤不存在: " + stepCode);
        }
        String nodeId = blankToNull(req.nodeId());
        if (nodeId != null && stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(flow.getId())
                .stream().noneMatch(s -> nodeId.equals(s.getNodeId()))) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "节点不存在: " + nodeId);
        }
        String effectParam = blankToNull(req.effectParam());
        if (CimFlowRule.EFFECT_ROUTE_OVERRIDE.equals(effect)) {
            if (effectParam == null) {
                throw new BizException(BizCode.PARAM_INVALID, "改派处置必须指定目标节点");
            }
            if (stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(flow.getId())
                    .stream().noneMatch(s -> effectParam.equals(s.getNodeId()))) {
                throw new BizException(BizCode.DATA_NOT_FOUND, "改派目标节点不存在: " + effectParam);
            }
        }
        if (CimFlowRule.EFFECT_INSERT_SEQUENCE.equals(effect) && effectParam == null) {
            throw new BizException(BizCode.PARAM_INVALID, "插单处置必须指定插入步骤编码");
        }
        rule.setFamilyCode(flow.getFamilyCode());
        rule.setVersionNo(flow.getVersionNo());
        rule.setRuleName(req.ruleName());
        rule.setJoinPoint(joinPoint);
        rule.setStepCode(stepCode);
        rule.setNodeId(nodeId);
        rule.setEdgeType(edgeType);
        rule.setConditionExpr(condition);
        rule.setEffect(effect);
        rule.setEffectParam(effectParam);
        rule.setPriority(req.priority() == null ? 100 : req.priority());
        rule.setEnabled(req.enabled() == null ? Boolean.TRUE : req.enabled());
    }

    private List<RuleView> findByFamily(String familyCode, Integer versionNo) {
        return ruleRepository
                .findByFamilyCodeAndVersionNoAndDeletedFalseOrderByPriorityAscRuleCodeAsc(
                        familyCode, versionNo)
                .stream()
                .map(r -> toView(r, requireFlowByFamily(familyCode, versionNo)))
                .toList();
    }

    private RuleView toView(CimFlowRule rule, CimFlow flow) {
        return new RuleView(rule.getId(), flow.getId(), rule.getFamilyCode(), rule.getVersionNo(),
                rule.getRuleCode(), rule.getRuleName(), rule.getJoinPoint(),
                rule.getStepCode(), rule.getNodeId(), rule.getEdgeType(),
                rule.getConditionExpr(), rule.getEffect(), rule.getEffectParam(),
                rule.getPriority(), rule.getEnabled(), rule.getTemplateKey());
    }

    private CimFlow requireFlow(String flowId) {
        return flowRepository.findByIdAndDeletedFalse(flowId)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "流程不存在: " + flowId));
    }

    private CimFlow requireEditableFlow(String flowId) {
        CimFlow flow = requireFlow(flowId);
        requireEditable(flow);
        return flow;
    }

    private CimFlow requireEditableFlowByFamily(String familyCode, Integer versionNo) {
        CimFlow flow = requireFlowByFamily(familyCode, versionNo);
        requireEditable(flow);
        return flow;
    }

    private CimFlow requireFlowByFamily(String familyCode, Integer versionNo) {
        return flowRepository.findByFamilyCodeAndDeletedFalse(familyCode).stream()
                .filter(f -> versionNo != null && versionNo.equals(f.getVersionNo()))
                .findFirst()
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND,
                        "流程版本不存在: " + familyCode + " V" + versionNo));
    }

    private void requireEditable(CimFlow flow) {
        if (!CimFlow.STATUS_UNFROZEN.equals(flow.getStatus())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "仅未冻结版本可维护规则（当前: " + flow.getStatus() + "）");
        }
    }

    private CimFlowRule requireRule(String id) {
        return ruleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "规则不存在: " + id));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
