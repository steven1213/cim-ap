package com.cim.mds.server.flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cim.mds.server.equipment.CimEquipmentTypeRepository;
import com.cim.mds.server.flow.CanvasXmlSupport.CanvasBin;
import com.cim.mds.server.flow.CanvasXmlSupport.CanvasDoc;
import com.cim.mds.server.flow.CanvasXmlSupport.CanvasEdge;
import com.cim.mds.server.flow.CanvasXmlSupport.CanvasNode;
import com.cim.mds.server.flow.CanvasXmlSupport.CanvasQtime;
import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.mds.server.site.CimTechnologyRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import java.math.BigDecimal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 制造流程（Flow）服务——XML 画布为源 + 解析产物表 + 图校验守门 + 生命周期。
 *
 * <p><b>存储口径（2026-11-16 用户定稿）</b>：{@code cim_flow.flow_xml} 是画布 XML
 * 原文件（节点+边+坐标完整序列化），<b>渲染画布和修改画布都以它为源</b>（前端
 * DOMParser 渲染、保存时整份回传）；每次保存（{@link #saveXml}）后由后端重新解析，
 * 重建 {@link CimFlowStep}/{@link CimFlowEdge} 解析产物表——step 表按 XML 原顺序
 * （step_no=1..n）保留节点 + 业务字段解析成列，供结构化查询与 MES 消费。</p>
 *
 * <p><b>保存时守门</b>：XML 语法/属性（{@link CanvasXmlSupport}）+ 业务结构
 * （stepCode 唯一 / 端点存在 / 边语义合法 / equipType 引用真实 / qtime 上下限）。
 * <b>发布时守门</b>（{@link #validateGraph}）：唯一 START + 至少一 END + START 沿
 * MAIN 可达 END + REWORK 回边目标须是起点上游（防死循环）+ 忽略 REWORK 边全图无环。
 * 草稿期画布自由编辑，图贯通性只在 validate/publish 把关。</p>
 *
 * <p>版本生命周期（V14）：UNFROZEN（可编辑）→ FROZEN（冻结待激活）→ ACTIVE（生效，
 * 同族唯一，激活时旧 ACTIVE 自动降级 FROZEN）→ DISABLED（禁用）；改冻结/生效版本 =
 * copy 出同族新版本（版本号递增）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAdminService {

    /** 数据字典码（V12 Java 种子）：流程步骤类型业务词表。 */
    public static final String DICT_PROCESS_STEP_TYPE = "mds.process_step_type";

    /** 合法 step_type 全集（结构性两类 + 业务四类 + SUBFLOW 引用节点 + SPLIT/MERGE 结构节点）。 */
    private static final Set<String> VALID_STEP_TYPES = Set.of(
            CimFlowStep.TYPE_START, CimFlowStep.TYPE_END,
            CimFlowStep.TYPE_PROCESS, CimFlowStep.TYPE_INSPECT,
            CimFlowStep.TYPE_REWORK, CimFlowStep.TYPE_DECISION, CimFlowStep.TYPE_SUBFLOW,
            CimFlowStep.TYPE_SPLIT, CimFlowStep.TYPE_MERGE);

    /** 合法批处理模式（V13；空 = 默认串行）。 */
    private static final Set<String> VALID_BATCH_MODES = Set.of("SERIAL", "PARALLEL", "BATCH");

    /** 合法跨站 Q-time 超限动作（V14）。 */
    private static final Set<String> VALID_QTIME_ACTIONS = Set.of(
            CimFlowQtime.ACTION_HOLD, CimFlowQtime.ACTION_SCRAP);

    /** 合法 flow_kind 全集。 */
    private static final Set<String> VALID_FLOW_KINDS = Set.of(CimFlow.KIND_MAIN, CimFlow.KIND_SUB);

    /** 合法 edge_type 全集（四语义边）。 */
    private static final Set<String> VALID_EDGE_TYPES = Set.of(
            CimFlowEdge.TYPE_MAIN, CimFlowEdge.TYPE_PASS,
            CimFlowEdge.TYPE_FAIL, CimFlowEdge.TYPE_REWORK);

    /** 抽样率上限 100。 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CimFlowRepository flowRepository;
    private final CimFlowStepRepository stepRepository;
    private final CimFlowEdgeRepository edgeRepository;
    private final CimFlowBinRepository binRepository;
    private final CimFlowQtimeRepository qtimeRepository;
    private final CimTechnologyRepository technologyRepository;
    private final CimEquipmentTypeRepository equipmentTypeRepository;
    private final com.cim.mds.server.equipment.CimCapabilityRepository capabilityRepository;
    private final CimOperationRepository operationRepository;
    private final NamingRuleAdminService namingRuleService;

    // ------------------------------------------------------------------ 视图 / 请求

    /** 流程主档视图（flowXml = 画布源文件原样回传，前端直接渲染）。 */
    public record FlowView(String id, String flowCode, String flowName, String techCode,
                           String status, String flowXml, String remark, String description,
                           String flowKind, int stepCount,
                           String familyCode, Integer versionNo) {
    }

    /** 解析产物节点视图（stepNo = XML 原顺序； nodeId = 边引用锚点；operationCode = 引用工序库，V20；
     *  capabilityCode/edcPlanCode = 能力需求/EDC Plan 覆写（V21，可空 = 继承工序默认）。 */
    public record StepView(String id, String nodeId, String stepCode, String stepName,
                           String stepType, String operationCode, Integer stepNo, String recipeRef, String equipType,
                           Integer qtimeMin, Integer qtimeMax, Integer reworkLimit,
                           String flowRef, String batchMode, Integer batchSize,
                           Boolean holdPoint, String holdReason,
                           String inspectPlan, BigDecimal samplingRate,
                           String capabilityCode, String edcPlanCode) {
    }

    /** 解析产物边视图（from/to = XML 节点 id）。 */
    public record EdgeView(String id, String fromStepId, String toStepId,
                           String edgeType, String conditionExpr) {
    }

    /** 跨站 Q-time 视图（from/to = XML 节点 id）。 */
    public record QtimeView(String id, String fromNodeId, String toNodeId,
                            Integer maxMinutes, String action) {
    }

    /** 流程详情（主档含 XML + 全部解析产物；画布页一次拉全）。 */
    public record FlowDetail(FlowView flow, List<StepView> steps, List<EdgeView> edges,
                             List<QtimeView> qtimes) {
    }

    /**
     * 主档新建/更新请求。flowCode 可空：有启用规则留空自动占号、手填走正则守门；
     * 无启用规则时必须手填（与产品域语义一致）。flowKind MAIN/SUB（可空默认 MAIN）。
     */
    public record FlowReq(String flowCode, String flowName, String techCode,
                          String remark, String description, String flowKind) {
    }

    /** 画布保存请求：整份 XML 回传（单一事实源口径）。 */
    public record CanvasReq(String xml) {
    }

    /** 校验结果（问题清单 + 是否可发布；「校验」按钮与 publish 前置共用）。 */
    public record ValidationResult(boolean valid, List<String> problems) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<FlowView> list(String keyword) {
        return flowRepository.search(blankToNull(keyword)).stream()
                .map(f -> toView(f, countSteps(f.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public FlowDetail detail(String id) {
        CimFlow flow = requireFlow(id);
        List<StepView> steps = stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(id)
                .stream().map(FlowAdminService::toStepView).toList();
        List<EdgeView> edges = edgeRepository.findByFlowIdAndDeletedFalse(id)
                .stream().map(FlowAdminService::toEdgeView).toList();
        List<QtimeView> qtimes = qtimeRepository.findByFlowIdAndDeletedFalse(id)
                .stream().map(FlowAdminService::toQtimeView).toList();
        return new FlowDetail(toView(flow, steps.size()), steps, edges, qtimes);
    }

    // ------------------------------------------------------------------ 主档 CRUD

    @Transactional
    public FlowView create(FlowReq req) {
        requireText(req.flowName(), "flowName");
        String techCode = requireTech(req.techCode());
        Map<String, String> ctx = techCode == null ? Map.of() : Map.of("tech", techCode);
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_FLOW,
                blankToNull(req.flowCode()), ctx);
        if (flowRepository.findByFlowCodeAndDeletedFalse(code).isPresent()) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.flow.flowCodeDuplicate", code);
        }
        CimFlow entity = new CimFlow();
        entity.setFlowCode(code);
        entity.setFlowName(req.flowName().trim());
        entity.setTechCode(techCode);
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setFlowKind(requireFlowKind(req.flowKind()));
        entity.setStatus(CimFlow.STATUS_UNFROZEN);
        entity.setFamilyCode(code); // 新建 = 自成版本族
        entity.setVersionNo(1);
        return toView(flowRepository.save(entity), 0);
    }

    /** 主档更新（只吃名称/技术/备注/种类；flowCode 与画布不在这里动）。 */
    @Transactional
    public FlowView update(String id, FlowReq req) {
        CimFlow entity = requireFlow(id);
        requireDraft(entity);
        requireText(req.flowName(), "flowName");
        entity.setFlowName(req.flowName().trim());
        entity.setTechCode(requireTech(req.techCode()));
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setFlowKind(requireFlowKind(req.flowKind()));
        return toView(flowRepository.save(entity), countSteps(id));
    }

    /** 删除（UNFROZEN 草稿或 DISABLED 已淘汰；XML 随主档走，解析产物与 bin/qtime 一并物理删）。 */
    @Transactional
    public void delete(String id) {
        CimFlow flow = requireFlow(id);
        if (!CimFlow.STATUS_UNFROZEN.equals(flow.getStatus())
                && !CimFlow.STATUS_DISABLED.equals(flow.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.onlyDraftOrDisabledDeletable", flow.getStatus());
        }
        edgeRepository.findByFlowIdAndDeletedFalse(id).forEach(edgeRepository::delete);
        stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(id).forEach(stepRepository::delete);
        binRepository.findByFlowIdAndDeletedFalse(id).forEach(binRepository::delete);
        qtimeRepository.findByFlowIdAndDeletedFalse(id).forEach(qtimeRepository::delete);
        flowRepository.delete(flow);
    }

    // ------------------------------------------------------------------ 画布保存（XML 单一事实源）

    /**
     * 画布保存：整份 XML 入库（flow_xml）→ 重新解析 → 重建解析产物表
     * （cim_flow_step 按 XML 原顺序 step_no=1..n；cim_flow_edge 端点引用 node_id）。
     * 一个事务；仅 DRAFT 可保存。保存即做业务结构校验；图贯通校验留给 validate/publish。
     */
    @Transactional
    public FlowDetail saveXml(String id, CanvasReq req) {
        CimFlow flow = requireFlow(id);
        requireDraft(flow);
        String xml = req == null ? null : req.xml();
        CanvasDoc doc = CanvasXmlSupport.parse(xml);
        List<CanvasNode> nodes = doc.nodes();
        List<CanvasEdge> edges = doc.edges();
        if (nodes.isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.flow.canvasEmpty");
        }

        // ---- 节点校验：类型白名单 / id 与 stepCode 唯一 / 引用真实 / qtime / SUBFLOW / 批处理 / bin
        Set<String> nodeIds = new HashSet<>();
        Set<String> stepCodes = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            CanvasNode n = nodes.get(i);
            if (!VALID_STEP_TYPES.contains(n.stepType())) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.flow.nodeStepTypeInvalid", i + 1, n.stepType());
            }
            if (!nodeIds.add(n.id())) {
                throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.flow.nodeIdDuplicate", n.id());
            }
            if (!stepCodes.add(n.stepCode())) {
                throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.flow.stepCodeDuplicate", n.stepCode());
            }
            // 工序引用守门（V20）：仅业务工序三类可引用；须存在且 ACTIVE；
            // 带出标准值由前端在「选工序」时回填（后端只存引用，不静默合并覆盖值）
            if (n.operationCode() != null && !n.operationCode().isBlank()) {
                if (!CimFlowStep.TYPE_PROCESS.equals(n.stepType())
                        && !CimFlowStep.TYPE_INSPECT.equals(n.stepType())
                        && !CimFlowStep.TYPE_REWORK.equals(n.stepType())) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.opRefStepTypeInvalid", n.stepCode());
                }
                CimOperation op = operationRepository
                        .findByOpCodeAndDeletedFalse(n.operationCode().trim())
                        .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                                "mds.biz.flow.opRefNotFound", n.operationCode().trim()));
                if (!CimOperation.STATUS_ACTIVE.equals(op.getStatus())) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.opRefNotActive", n.operationCode().trim(), op.getStatus());
                }
                if (CimFlowStep.TYPE_INSPECT.equals(n.stepType())
                        && !CimOperation.TYPE_INSPECT.equals(op.getOpType())) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.inspectRefMustBeInspect", n.operationCode().trim());
                }
            }
            if (n.equipType() != null
                    && equipmentTypeRepository.findByTypeCodeAndDeletedFalse(n.equipType()).isEmpty()) {
                throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.flow.equipTypeNotFound", n.equipType());
            }
            // V21：能力需求非空才校验存在（AMAT 口径——经设备能力关联匹配可加工设备）
            if (n.capabilityCode() != null
                    && capabilityRepository.findByCapabilityCodeAndDeletedFalse(n.capabilityCode()).isEmpty()) {
                throw BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.capabilityNotFound", n.capabilityCode(), n.stepCode());
            }
            checkQtime(n.qtimeMin(), n.qtimeMax(), n.stepCode());
            // SUBFLOW：flowRef 必填且引用真实（发布状态在发布守门再查）
            if (CimFlowStep.TYPE_SUBFLOW.equals(n.stepType())) {
                if (n.flowRef() == null || n.flowRef().isBlank()) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.subflowRefRequired", n.stepCode());
                }
                if (flowRepository.findByFlowCodeAndDeletedFalse(n.flowRef()).isEmpty()) {
                    throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.flow.flowRefNotFound", n.flowRef());
                }
            } else if (n.flowRef() != null && !n.flowRef().isBlank()) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.flow.flowRefOnlySubflow", n.stepCode());
            }
            // 批处理模式白名单 + 装载量
            if (n.batchMode() != null && !VALID_BATCH_MODES.contains(n.batchMode())) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.flow.batchModeInvalid", n.stepCode(), n.batchMode());
            }
            if (n.batchSize() != null) {
                Integer bs = parseNullableInt(n.batchSize(), "batchSize", n.stepCode());
                if (bs != null && bs <= 0) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.batchSizePositive", n.stepCode(), bs);
                }
            }
            // bin 词表语法：binType 白名单 + 节点内 code 唯一
            Set<String> binCodes = new HashSet<>();
            for (CanvasBin b : doc.bins()) {
                if (!b.nodeId().equals(n.id())) {
                    continue;
                }
                if (!CimFlowBin.TYPE_PASS.equals(b.binType()) && !CimFlowBin.TYPE_FAIL.equals(b.binType())) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.binTypeInvalid", n.stepCode(), b.binCode(), b.binType());
                }
                if (!binCodes.add(b.binCode())) {
                    throw BizException.of(BizCode.DATA_DUPLICATE,
                            "mds.biz.flow.binCodeDuplicate", n.stepCode(), b.binCode());
                }
            }
            // 检验配置（V14）：仅 INSPECT 可设检验计划/抽样率；抽样率 0-100
            if (!CimFlowStep.TYPE_INSPECT.equals(n.stepType())) {
                if (n.inspectPlan() != null) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.inspectPlanOnly", n.stepCode());
                }
                if (n.samplingRate() != null) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.samplingRateOnly", n.stepCode());
                }
            } else {
                if (n.samplingRate() != null) {
                    BigDecimal rate = parseNullableDecimal(n.samplingRate(), "samplingRate", n.stepCode());
                    if (rate != null
                            && (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(HUNDRED) > 0)) {
                        throw BizException.of(BizCode.PARAM_INVALID,
                                "mds.biz.flow.samplingRateRange", n.stepCode(), rate);
                    }
                }
            }
        }
        // ---- 跨站 Q-time 守门（V14）：from/to 真实 + 非自站 + maxMinutes>0 + action 白名单 + 同对站去重
        Set<String> qtimePairs = new HashSet<>();
        for (int i = 0; i < doc.qtimes().size(); i++) {
            CanvasQtime q = doc.qtimes().get(i);
            if (!nodeIds.contains(q.from()) || !nodeIds.contains(q.to())) {
                throw BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.qtimeNodeNotFound", i + 1);
            }
            if (q.from().equals(q.to())) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.flow.qtimeSelfSite");
            }
            Integer maxMinutes = parseNullableInt(q.maxMinutes(), "maxMinutes", nodeCode(nodes, q.from()));
            if (maxMinutes == null || maxMinutes <= 0) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.flow.qtimeMaxPositive", i + 1, q.maxMinutes());
            }
            if (!VALID_QTIME_ACTIONS.contains(q.action())) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.flow.qtimeActionInvalid", i + 1, q.action());
            }
            if (!qtimePairs.add(q.from() + "->" + q.to())) {
                throw BizException.of(BizCode.DATA_DUPLICATE,
                        "mds.biz.flow.qtimeDuplicate",
                        nodeCode(nodes, q.from()), nodeCode(nodes, q.to()));
            }
        }
        // ---- 边校验：端点存在 / 非自环 / 语义合法（DECISION 出边必须 PASS/FAIL）+ 条件命中 bin 词表
        Map<String, Set<String>> binsByNode = new HashMap<>();
        for (CanvasBin b : doc.bins()) {
            binsByNode.computeIfAbsent(b.nodeId(), k -> new HashSet<>()).add(b.binCode());
        }
        for (int i = 0; i < edges.size(); i++) {
            CanvasEdge e = edges.get(i);
            if (e.from().equals(e.to())) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.flow.edgeSelfLoop", i + 1);
            }
            if (!nodeIds.contains(e.from()) || !nodeIds.contains(e.to())) {
                throw BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.edgeNodeNotFound", i + 1);
            }
            if (!VALID_EDGE_TYPES.contains(e.edgeType())) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.flow.edgeTypeInvalid", i + 1, e.edgeType());
            }
            CanvasNode fromNode = nodes.stream().filter(n -> n.id().equals(e.from()))
                    .findFirst().orElseThrow();
            // SPLIT/MERGE 结构节点（V14）：出入边语义仅 MAIN（拆分/汇合不参与分档/返工）
            CanvasNode toNode = nodes.stream().filter(n -> n.id().equals(e.to()))
                    .findFirst().orElseThrow();
            boolean structural = CimFlowStep.TYPE_SPLIT.equals(fromNode.stepType())
                    || CimFlowStep.TYPE_MERGE.equals(fromNode.stepType())
                    || CimFlowStep.TYPE_SPLIT.equals(toNode.stepType())
                    || CimFlowStep.TYPE_MERGE.equals(toNode.stepType());
            boolean ok;
            if (structural) {
                ok = CimFlowEdge.TYPE_MAIN.equals(e.edgeType());
            } else {
                boolean decision = CimFlowStep.TYPE_DECISION.equals(fromNode.stepType());
                ok = decision
                        ? CimFlowEdge.TYPE_PASS.equals(e.edgeType()) || CimFlowEdge.TYPE_FAIL.equals(e.edgeType())
                        : CimFlowEdge.TYPE_MAIN.equals(e.edgeType()) || CimFlowEdge.TYPE_REWORK.equals(e.edgeType());
            }
            if (!ok) {
                if (structural) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.structuralEdgeMainOnly", i + 1, e.edgeType());
                }
                throw BizException.of(BizCode.PARAM_INVALID,
                        CimFlowStep.TYPE_DECISION.equals(fromNode.stepType())
                                ? "mds.biz.flow.decisionEdgePassFail" : "mds.biz.flow.plainEdgeMainRework",
                        i + 1, e.edgeType());
            }
            // bin 词表已定义的 DECISION 节点：出边 condition 必须命中词表（防手写条件漂移）
            Set<String> nodeBins = binsByNode.get(e.from());
            if (CimFlowStep.TYPE_DECISION.equals(fromNode.stepType())
                    && nodeBins != null && !nodeBins.isEmpty()
                    && (e.condition() == null || !nodeBins.contains(e.condition()))) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.flow.conditionNotInBins",
                        fromNode.stepCode(), String.valueOf(e.condition()), String.join("/", nodeBins));
            }
        }

        // ---- XML 入库 + 重建解析产物（全量替换；先删后插并 flush 强制顺序，
        //      否则同事务里 Hibernate 可能先 insert 后 delete，撞 uk_cim_flow_edge/step_code/bin）
        flow.setFlowXml(xml);
        flowRepository.save(flow);
        edgeRepository.findByFlowIdAndDeletedFalse(id).forEach(edgeRepository::delete);
        stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(id).forEach(stepRepository::delete);
        binRepository.findByFlowIdAndDeletedFalse(id).forEach(binRepository::delete);
        qtimeRepository.findByFlowIdAndDeletedFalse(id).forEach(qtimeRepository::delete);
        edgeRepository.flush();
        stepRepository.flush();
        binRepository.flush();
        qtimeRepository.flush();
        for (int i = 0; i < nodes.size(); i++) {
            CanvasNode n = nodes.get(i);
            CimFlowStep entity = new CimFlowStep();
            entity.setFlowId(id);
            entity.setNodeId(n.id());
            entity.setStepCode(n.stepCode());
            entity.setStepName(n.stepName());
            entity.setStepType(n.stepType());
            entity.setStepNo(i + 1); // XML 原顺序（1..n）
            entity.setOperationCode(n.operationCode() == null || n.operationCode().isBlank()
                    ? null : n.operationCode().trim());
            entity.setRecipeRef(n.recipeRef());
            entity.setEquipType(n.equipType());
            entity.setCapabilityCode(n.capabilityCode());
            entity.setEdcPlanCode(n.edcPlanCode());
            entity.setQtimeMin(parseNullableInt(n.qtimeMin(), "qtimeMin", n.stepCode()));
            entity.setQtimeMax(parseNullableInt(n.qtimeMax(), "qtimeMax", n.stepCode()));
            entity.setReworkLimit(parseNullableInt(n.reworkLimit(), "reworkLimit", n.stepCode()));
            entity.setFlowRef(n.flowRef());
            entity.setBatchMode(n.batchMode());
            entity.setBatchSize(parseNullableInt(n.batchSize(), "batchSize", n.stepCode()));
            entity.setHoldPoint(n.holdPoint());
            entity.setHoldReason(n.holdReason());
            entity.setInspectPlan(n.inspectPlan());
            entity.setSamplingRate(parseNullableDecimal(n.samplingRate(), "samplingRate", n.stepCode()));
            stepRepository.save(entity);
        }
        for (CanvasEdge e : edges) {
            CimFlowEdge entity = new CimFlowEdge();
            entity.setFlowId(id);
            entity.setFromStepId(e.from());
            entity.setToStepId(e.to());
            entity.setEdgeType(e.edgeType());
            entity.setConditionExpr(e.condition());
            edgeRepository.save(entity);
        }
        for (CanvasBin b : doc.bins()) {
            CimFlowBin entity = new CimFlowBin();
            entity.setFlowId(id);
            entity.setNodeId(b.nodeId());
            entity.setBinCode(b.binCode());
            entity.setBinName(b.binName());
            entity.setBinType(b.binType());
            entity.setSortNo(b.sortNo());
            binRepository.save(entity);
        }
        for (CanvasQtime q : doc.qtimes()) {
            CimFlowQtime entity = new CimFlowQtime();
            entity.setFlowId(id);
            entity.setFromNodeId(q.from());
            entity.setToNodeId(q.to());
            entity.setMaxMinutes(parseNullableInt(q.maxMinutes(), "maxMinutes", nodeCode(nodes, q.from())));
            entity.setAction(q.action());
            qtimeRepository.save(entity);
        }
        return detail(id);
    }

    // ------------------------------------------------------------------ 校验 / 生命周期

    /** 只校验不落库（画布「校验」按钮；基于库内解析产物）。 */
    @Transactional(readOnly = true)
    public ValidationResult validate(String id) {
        requireFlow(id);
        return validateGraph(id);
    }

    /**
     * 冻结：UNFROZEN → FROZEN（内容定稿，不再允许修改，等待激活）。
     * 图校验通过才允许冻结（冻结版必须是可发布的完整工艺）。
     */
    @Transactional
    public FlowView freeze(String id) {
        CimFlow flow = requireFlow(id);
        if (!CimFlow.STATUS_UNFROZEN.equals(flow.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.onlyUnfrozenFreezable", flow.getStatus());
        }
        ValidationResult result = validateGraph(id);
        if (!result.valid()) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.flow.validateFailFreeze", String.join("；", result.problems()));
        }
        flow.setStatus(CimFlow.STATUS_FROZEN);
        return toView(flowRepository.save(flow), countSteps(id));
    }

    /** 解冻：FROZEN → UNFROZEN（定稿前发现要改，退回草稿）。 */
    @Transactional
    public FlowView unfreeze(String id) {
        CimFlow flow = requireFlow(id);
        if (!CimFlow.STATUS_FROZEN.equals(flow.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.onlyFrozenUnfreezable", flow.getStatus());
        }
        flow.setStatus(CimFlow.STATUS_UNFROZEN);
        return toView(flowRepository.save(flow), countSteps(id));
    }

    /**
     * 激活：FROZEN → ACTIVE（生产线现行版本）。图校验通过才放行；
     * <b>单 Active 约束</b>：同版本族内当前 ACTIVE 自动降级 FROZEN——生产线上
     * 永远只认一条「现行有效」工艺路线。
     */
    @Transactional
    public FlowView activate(String id) {
        CimFlow flow = requireFlow(id);
        if (!CimFlow.STATUS_FROZEN.equals(flow.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.onlyFrozenActivatable", flow.getStatus());
        }
        ValidationResult result = validateGraph(id);
        if (!result.valid()) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.flow.validateFailActivate", String.join("；", result.problems()));
        }
        String family = flow.getFamilyCode() == null ? flow.getFlowCode() : flow.getFamilyCode();
        flowRepository.findByFamilyCodeAndDeletedFalseAndStatus(family, CimFlow.STATUS_ACTIVE)
                .filter(active -> !active.getId().equals(id))
                .ifPresent(old -> {
                    old.setStatus(CimFlow.STATUS_FROZEN);
                    flowRepository.save(old);
                });
        flow.setStatus(CimFlow.STATUS_ACTIVE);
        return toView(flowRepository.save(flow), countSteps(id));
    }

    /** 禁用：FROZEN / ACTIVE → DISABLED（淘汰，不再允许任何使用）。 */
    @Transactional
    public FlowView disable(String id) {
        CimFlow flow = requireFlow(id);
        if (!CimFlow.STATUS_FROZEN.equals(flow.getStatus())
                && !CimFlow.STATUS_ACTIVE.equals(flow.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.onlyFrozenOrActiveDisablable", flow.getStatus());
        }
        flow.setStatus(CimFlow.STATUS_DISABLED);
        return toView(flowRepository.save(flow), countSteps(id));
    }

    /**
     * 复制为同族新版本：新 UNFROZEN 版本（flowCode 重新占号——<b>要求 FLOW 启用规则</b>；
     * 族内版本号 max+1，名称 = 源名 + V{n}）；XML 原样带过 + 解析产物原样克隆
     * （node_id/step_no 保持）。冻结/生效版本的改动一律走复制。
     */
    @Transactional
    public FlowView copy(String id) {
        CimFlow source = requireFlow(id);
        Map<String, String> ctx = source.getTechCode() == null
                ? Map.of() : Map.of("tech", source.getTechCode());
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_FLOW, null, ctx);
        CimFlow entity = new CimFlow();
        entity.setFlowCode(code);
        entity.setFlowName(source.getFlowName() + " V"
                + (nextVersionNo(source.getFamilyCode() == null ? source.getFlowCode() : source.getFamilyCode())));
        entity.setTechCode(source.getTechCode());
        entity.setRemark(source.getRemark());
        entity.setDescription(source.getDescription());
        entity.setFlowKind(source.getFlowKind());
        entity.setStatus(CimFlow.STATUS_UNFROZEN);
        entity.setFamilyCode(source.getFamilyCode() == null ? source.getFlowCode() : source.getFamilyCode());
        entity.setVersionNo(nextVersionNo(entity.getFamilyCode()));
        entity.setFlowXml(source.getFlowXml()); // XML 源原样复制
        entity = flowRepository.save(entity);
        String newId = entity.getId();
        for (CimFlowStep s : stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(id)) {
            CimFlowStep clone = new CimFlowStep();
            clone.setFlowId(newId);
            clone.setNodeId(s.getNodeId());
            clone.setStepCode(s.getStepCode());
            clone.setStepName(s.getStepName());
            clone.setStepType(s.getStepType());
            clone.setStepNo(s.getStepNo());
            clone.setOperationCode(s.getOperationCode());
            clone.setRecipeRef(s.getRecipeRef());
            clone.setEquipType(s.getEquipType());
            clone.setCapabilityCode(s.getCapabilityCode());
            clone.setEdcPlanCode(s.getEdcPlanCode());
            clone.setQtimeMin(s.getQtimeMin());
            clone.setQtimeMax(s.getQtimeMax());
            clone.setReworkLimit(s.getReworkLimit());
            clone.setFlowRef(s.getFlowRef());
            clone.setBatchMode(s.getBatchMode());
            clone.setBatchSize(s.getBatchSize());
            clone.setHoldPoint(Boolean.TRUE.equals(s.getHoldPoint()));
            clone.setHoldReason(s.getHoldReason());
            clone.setInspectPlan(s.getInspectPlan());
            clone.setSamplingRate(s.getSamplingRate());
            stepRepository.save(clone);
        }
        for (CimFlowEdge e : edgeRepository.findByFlowIdAndDeletedFalse(id)) {
            CimFlowEdge clone = new CimFlowEdge();
            clone.setFlowId(newId);
            clone.setFromStepId(e.getFromStepId());
            clone.setToStepId(e.getToStepId());
            clone.setEdgeType(e.getEdgeType());
            clone.setConditionExpr(e.getConditionExpr());
            edgeRepository.save(clone);
        }
        for (CimFlowBin b : binRepository.findByFlowIdAndDeletedFalse(id)) {
            CimFlowBin clone = new CimFlowBin();
            clone.setFlowId(newId);
            clone.setNodeId(b.getNodeId());
            clone.setBinCode(b.getBinCode());
            clone.setBinName(b.getBinName());
            clone.setBinType(b.getBinType());
            clone.setSortNo(b.getSortNo());
            binRepository.save(clone);
        }
        for (CimFlowQtime q : qtimeRepository.findByFlowIdAndDeletedFalse(id)) {
            CimFlowQtime clone = new CimFlowQtime();
            clone.setFlowId(newId);
            clone.setFromNodeId(q.getFromNodeId());
            clone.setToNodeId(q.getToNodeId());
            clone.setMaxMinutes(q.getMaxMinutes());
            clone.setAction(q.getAction());
            qtimeRepository.save(clone);
        }
        return toView(entity, countSteps(newId));
    }

    // ------------------------------------------------------------------ 图校验（发布守门核心）

    /**
     * 图校验全量规则（validate/publish 共用，基于库内解析产物）：见类注释。
     * 节点键 = node_id（XML 节点 id，与边表引用一致）。
     */
    private ValidationResult validateGraph(String flowId) {
        List<String> problems = new ArrayList<>();
        List<CimFlowStep> steps = stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(flowId);
        List<CimFlowEdge> edges = edgeRepository.findByFlowIdAndDeletedFalse(flowId);
        if (steps.isEmpty()) {
            return new ValidationResult(false, List.of("流程没有任何步骤节点（画布尚未保存）"));
        }
        Map<String, CimFlowStep> byNodeId = new HashMap<>();
        for (CimFlowStep s : steps) {
            byNodeId.put(s.getNodeId(), s);
        }
        // 规则 1：唯一 START + 至少一 END
        List<CimFlowStep> starts = steps.stream()
                .filter(s -> CimFlowStep.TYPE_START.equals(s.getStepType())).toList();
        List<CimFlowStep> ends = steps.stream()
                .filter(s -> CimFlowStep.TYPE_END.equals(s.getStepType())).toList();
        if (starts.size() != 1) {
            problems.add("必须恰好有一个 START 节点，当前 " + starts.size() + " 个");
        }
        if (ends.isEmpty()) {
            problems.add("至少需要一个 END 节点");
        }
        // 邻接表（端点缺失的边先记问题再剔除）
        Map<String, List<CimFlowEdge>> out = new HashMap<>();
        for (CimFlowEdge e : edges) {
            if (!byNodeId.containsKey(e.getFromStepId()) || !byNodeId.containsKey(e.getToStepId())) {
                problems.add("边端点不存在（解析产物不一致，请重新保存画布）");
                continue;
            }
            out.computeIfAbsent(e.getFromStepId(), k -> new ArrayList<>()).add(e);
        }
        // 规则 5：边语义（出边类型 vs 起点节点类型）+ bin 词表命中
        Map<String, Set<String>> binsByNode = new HashMap<>();
        for (CimFlowBin b : binRepository.findByFlowIdAndDeletedFalse(flowId)) {
            binsByNode.computeIfAbsent(b.getNodeId(), k -> new HashSet<>()).add(b.getBinCode());
        }
        for (Map.Entry<String, List<CimFlowEdge>> entry : out.entrySet()) {
            String fromType = byNodeId.get(entry.getKey()).getStepType();
            boolean structuralFrom = CimFlowStep.TYPE_SPLIT.equals(fromType)
                    || CimFlowStep.TYPE_MERGE.equals(fromType);
            for (CimFlowEdge e : entry.getValue()) {
                boolean ok;
                if (structuralFrom) {
                    ok = CimFlowEdge.TYPE_MAIN.equals(e.getEdgeType());
                } else {
                    boolean decision = CimFlowStep.TYPE_DECISION.equals(fromType);
                    ok = decision
                            ? CimFlowEdge.TYPE_PASS.equals(e.getEdgeType())
                                    || CimFlowEdge.TYPE_FAIL.equals(e.getEdgeType())
                            : CimFlowEdge.TYPE_MAIN.equals(e.getEdgeType())
                                    || CimFlowEdge.TYPE_REWORK.equals(e.getEdgeType());
                }
                if (!ok) {
                    problems.add(byNodeId.get(entry.getKey()).getStepCode() + "（" + fromType
                            + "）的出边语义非法: " + e.getEdgeType());
                }
                Set<String> nodeBins = binsByNode.get(entry.getKey());
                if (CimFlowStep.TYPE_DECISION.equals(fromType)
                        && nodeBins != null && !nodeBins.isEmpty()
                        && (e.getConditionExpr() == null || !nodeBins.contains(e.getConditionExpr()))) {
                    problems.add(byNodeId.get(entry.getKey()).getStepCode() + " 的出边条件 "
                            + e.getConditionExpr() + " 不在其 bin 词表内（可用: "
                            + String.join("/", nodeBins) + "）");
                }
            }
        }
        // 规则 6：SUBFLOW 引用——目标须已发布 + 引用环检测（跨流程 DFS）
        for (CimFlowStep s : steps) {
            if (!CimFlowStep.TYPE_SUBFLOW.equals(s.getStepType()) || s.getFlowRef() == null) {
                continue;
            }
            CimFlow ref = flowRepository.findByFlowCodeAndDeletedFalse(s.getFlowRef()).orElse(null);
            if (ref == null) {
                problems.add("子流程节点 " + s.getStepCode() + " 引用的流程不存在: " + s.getFlowRef());
            } else if (!CimFlow.STATUS_ACTIVE.equals(ref.getStatus())) {
                problems.add("子流程节点 " + s.getStepCode() + " 引用的流程未发布: "
                        + s.getFlowRef() + "（" + ref.getStatus() + "）");
            }
        }
        String refCycle = findFlowRefCycle(flowId);
        if (refCycle != null) {
            problems.add("子流程引用存在环路: " + refCycle);
        }
        // 规则 7（V14）：SPLIT 出边 ≥2 / MERGE 入边 ≥2（拆分/汇合的结构语义）
        Map<String, Integer> outDegree = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (CimFlowEdge e : edges) {
            if (!byNodeId.containsKey(e.getFromStepId()) || !byNodeId.containsKey(e.getToStepId())) {
                continue;
            }
            outDegree.merge(e.getFromStepId(), 1, Integer::sum);
            inDegree.merge(e.getToStepId(), 1, Integer::sum);
        }
        for (CimFlowStep s : steps) {
            if (CimFlowStep.TYPE_SPLIT.equals(s.getStepType())
                    && outDegree.getOrDefault(s.getNodeId(), 0) < 2) {
                problems.add("分批节点 " + s.getStepCode() + " 至少需要 2 条出边（当前 "
                        + outDegree.getOrDefault(s.getNodeId(), 0) + " 条）");
            }
            if (CimFlowStep.TYPE_MERGE.equals(s.getStepType())
                    && inDegree.getOrDefault(s.getNodeId(), 0) < 2) {
                problems.add("合批节点 " + s.getStepCode() + " 至少需要 2 条入边（当前 "
                        + inDegree.getOrDefault(s.getNodeId(), 0) + " 条）");
            }
        }
        // 规则 8（V14）：跨站 Q-time 的 from 必须沿主线（MAIN/PASS/FAIL）可达 to
        List<CimFlowQtime> qtimes = qtimeRepository.findByFlowIdAndDeletedFalse(flowId);
        for (CimFlowQtime q : qtimes) {
            if (!byNodeId.containsKey(q.getFromNodeId()) || !byNodeId.containsKey(q.getToNodeId())) {
                problems.add("跨站 Q-time 引用了不存在的节点（请重新保存画布）");
                continue;
            }
            Set<String> reach = reachable(q.getFromNodeId(), edges,
                    Set.of(CimFlowEdge.TYPE_MAIN, CimFlowEdge.TYPE_PASS, CimFlowEdge.TYPE_FAIL),
                    byNodeId);
            if (!reach.contains(q.getToNodeId())) {
                problems.add("跨站 Q-time 起点站 " + byNodeId.get(q.getFromNodeId()).getStepCode()
                        + " 沿主线走不到终点站 " + byNodeId.get(q.getToNodeId()).getStepCode()
                        + "（约束悬空）");
            }
        }
        // 规则 3：忽略 REWORK 边后全图无环（DFS 三色标记）
        String cycle = findCycle(mainGraph(edges, byNodeId), starts.stream()
                .map(CimFlowStep::getNodeId).findFirst().orElse(null));
        if (cycle != null) {
            problems.add("流程存在环路（不含返工回边）: " + cycle);
        }
        // 规则 2 / 4：主线贯通 + 返工回边可走回
        // （贯通口径 = 沿正常流向边 MAIN/PASS/FAIL——决策边也是前进方向，只排除 REWORK 回边）
        if (starts.size() == 1) {
            String startId = starts.get(0).getNodeId();
            Set<String> mainReachable = reachable(startId, edges,
                    Set.of(CimFlowEdge.TYPE_MAIN, CimFlowEdge.TYPE_PASS, CimFlowEdge.TYPE_FAIL),
                    byNodeId);
            if (ends.stream().noneMatch(e -> mainReachable.contains(e.getNodeId()))) {
                problems.add("START 沿主线（MAIN）走不到任何 END 节点");
            }
            for (CimFlowEdge e : edges) {
                if (!CimFlowEdge.TYPE_REWORK.equals(e.getEdgeType())
                        || !byNodeId.containsKey(e.getFromStepId())) {
                    continue;
                }
                Set<String> backReach = reachable(e.getToStepId(), edges,
                        Set.of(CimFlowEdge.TYPE_MAIN, CimFlowEdge.TYPE_PASS, CimFlowEdge.TYPE_FAIL),
                        byNodeId);
                if (!backReach.contains(e.getFromStepId())) {
                    problems.add("返工回边 " + byNodeId.get(e.getFromStepId()).getStepCode()
                            + " → " + byNodeId.get(e.getToStepId()).getStepCode()
                            + " 的目标走不回起点（会形成死循环）");
                }
            }
        }
        return new ValidationResult(problems.isEmpty(), problems);
    }

    /** 环检测（DFS 三色标记），返回环路描述或 null；startId 为空时对全图跑。 */
    private String findCycle(Map<String, List<String>> graph, String startId) {
        List<String> roots = startId != null && graph.containsKey(startId)
                ? List.of(startId) : new ArrayList<>(graph.keySet());
        for (String node : roots) {
            String cycle = dfsCycle(graph, node, new HashSet<>(), new HashSet<>(), new ArrayDeque<>());
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private String dfsCycle(Map<String, List<String>> graph, String node,
                            Set<String> visited, Set<String> inStack, Deque<String> path) {
        visited.add(node);
        inStack.add(node);
        path.addLast(node);
        for (String next : graph.getOrDefault(node, List.of())) {
            if (inStack.contains(next)) {
                List<String> ring = new ArrayList<>(path);
                int at = ring.indexOf(next);
                ring = ring.subList(Math.max(at, 0), ring.size());
                ring.add(next);
                return String.join(" → ", ring);
            }
            if (!visited.contains(next)) {
                String cycle = dfsCycle(graph, next, visited, inStack, path);
                if (cycle != null) {
                    return cycle;
                }
            }
        }
        path.removeLast();
        inStack.remove(node);
        return null;
    }

    /** 邻接表：忽略 REWORK 边（DAG 检测口径）。 */
    private Map<String, List<String>> mainGraph(List<CimFlowEdge> edges, Map<String, CimFlowStep> byNodeId) {
        Map<String, List<String>> graph = new HashMap<>();
        for (CimFlowEdge e : edges) {
            if (!byNodeId.containsKey(e.getFromStepId()) || !byNodeId.containsKey(e.getToStepId())) {
                continue;
            }
            if (CimFlowEdge.TYPE_REWORK.equals(e.getEdgeType())) {
                continue;
            }
            graph.computeIfAbsent(e.getFromStepId(), k -> new ArrayList<>()).add(e.getToStepId());
        }
        return graph;
    }

    /** 从某节点沿指定类型边 BFS 的可达集合（键 = node_id）。 */
    private Set<String> reachable(String startId, List<CimFlowEdge> edges,
                                  Set<String> edgeTypes, Map<String, CimFlowStep> byNodeId) {
        Map<String, List<String>> graph = new HashMap<>();
        for (CimFlowEdge e : edges) {
            if (!byNodeId.containsKey(e.getFromStepId()) || !byNodeId.containsKey(e.getToStepId())) {
                continue;
            }
            if (!edgeTypes.contains(e.getEdgeType())) {
                continue;
            }
            graph.computeIfAbsent(e.getFromStepId(), k -> new ArrayList<>()).add(e.getToStepId());
        }
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            for (String next : graph.getOrDefault(node, List.of())) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    // ------------------------------------------------------------------ 内部

    /** 跨流程 SUBFLOW 引用环检测（从本流程出发 DFS flow_ref 关系，返回环描述或 null）。 */
    private String findFlowRefCycle(String flowId) {
        return dfsFlowRefs(flowId, new HashSet<>(), new HashSet<>(), new ArrayList<>());
    }

    private String dfsFlowRefs(String flowId, Set<String> visited, Set<String> inStack, List<String> path) {
        CimFlow f = flowRepository.findById(flowId).orElse(null);
        if (f == null || Boolean.TRUE.equals(f.getDeleted())) {
            return null;
        }
        visited.add(flowId);
        inStack.add(flowId);
        path.add(f.getFlowCode());
        List<String> refs = stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(flowId).stream()
                .filter(s -> CimFlowStep.TYPE_SUBFLOW.equals(s.getStepType()) && s.getFlowRef() != null)
                .map(CimFlowStep::getFlowRef)
                .toList();
        for (String ref : refs) {
            CimFlow target = flowRepository.findByFlowCodeAndDeletedFalse(ref).orElse(null);
            if (target == null) {
                continue;
            }
            if (inStack.contains(target.getId())) {
                List<String> ring = new ArrayList<>(path.subList(Math.max(path.indexOf(ref), 0), path.size()));
                ring.add(ref);
                return String.join(" → ", ring);
            }
            if (!visited.contains(target.getId())) {
                String cycle = dfsFlowRefs(target.getId(), visited, inStack, path);
                if (cycle != null) {
                    return cycle;
                }
            }
        }
        path.remove(path.size() - 1);
        inStack.remove(flowId);
        return null;
    }

    private CimFlow requireFlow(String id) {
        return flowRepository.findById(id)
                .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.flow.notFound", id));
    }

    /** 族内下一个版本号（max+1）。 */
    private int nextVersionNo(String familyCode) {
        return flowRepository.findByFamilyCodeAndDeletedFalse(familyCode).stream()
                .mapToInt(f -> f.getVersionNo() == null ? 1 : f.getVersionNo())
                .max().orElse(0) + 1;
    }

    private int countSteps(String flowId) {
        return stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(flowId).size();
    }

    private static void requireDraft(CimFlow flow) {
        if (!CimFlow.STATUS_UNFROZEN.equals(flow.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.onlyUnfrozenEditable", flow.getStatus());
        }
    }

    /** tech_code 引用守门（可空；填了就必须是库内制程技术）。 */
    private String requireTech(String techCode) {
        String code = blankToNull(techCode);
        if (code != null && technologyRepository.findByTechCodeAndDeletedFalse(code).isEmpty()) {
            throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.flow.techNotFound", code);
        }
        return code;
    }

    /** XML 属性整数解析（可空；非法值 → 400）。 */
    private static Integer parseNullableInt(String v, String field, String stepCode) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.flow.notInteger", stepCode, field, v);
        }
    }

    /** XML 属性小数解析（可空；非法值 → 400）。 */
    private static BigDecimal parseNullableDecimal(String v, String field, String stepCode) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.flow.notDecimal", stepCode, field, v);
        }
    }

    /** 按 node_id 找 stepCode（守门文案定位用；找不到回退 node_id）。 */
    private static String nodeCode(List<CanvasNode> nodes, String nodeId) {
        return nodes.stream().filter(n -> n.id().equals(nodeId)).findFirst()
                .map(CanvasNode::stepCode).orElse(nodeId);
    }

    private static void checkQtime(String min, String max, String stepCode) {
        Integer mn = parseNullableInt(min, "qtimeMin", stepCode);
        Integer mx = parseNullableInt(max, "qtimeMax", stepCode);
        if (mn != null && mx != null && mn > mx) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.flow.qtimeMinMax", stepCode, mn, mx);
        }
    }

    /** flow_kind 守门（可空默认 MAIN）。 */
    private String requireFlowKind(String kind) {
        String k = blankToNull(kind) == null ? CimFlow.KIND_MAIN : blankToNull(kind);
        if (!VALID_FLOW_KINDS.contains(k)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.flow.flowKindInvalid", k);
        }
        return k;
    }

    private static FlowView toView(CimFlow f, int stepCount) {
        return new FlowView(f.getId(), f.getFlowCode(), f.getFlowName(), f.getTechCode(),
                f.getStatus(), f.getFlowXml(), f.getRemark(), f.getDescription(),
                f.getFlowKind(), stepCount,
                f.getFamilyCode() == null ? f.getFlowCode() : f.getFamilyCode(),
                f.getVersionNo() == null ? 1 : f.getVersionNo());
    }

    private static StepView toStepView(CimFlowStep s) {
        return new StepView(s.getId(), s.getNodeId(), s.getStepCode(), s.getStepName(),
                s.getStepType(), s.getOperationCode(), s.getStepNo(), s.getRecipeRef(), s.getEquipType(),
                s.getQtimeMin(), s.getQtimeMax(), s.getReworkLimit(),
                s.getFlowRef(), s.getBatchMode(), s.getBatchSize(),
                s.getHoldPoint(), s.getHoldReason(),
                s.getInspectPlan(), s.getSamplingRate(),
                s.getCapabilityCode(), s.getEdcPlanCode());
    }

    private static QtimeView toQtimeView(CimFlowQtime q) {
        return new QtimeView(q.getId(), q.getFromNodeId(), q.getToNodeId(),
                q.getMaxMinutes(), q.getAction());
    }

    private static EdgeView toEdgeView(CimFlowEdge e) {
        return new EdgeView(e.getId(), e.getFromStepId(), e.getToStepId(),
                e.getEdgeType(), e.getConditionExpr());
    }

    private static void requireText(String v, String field) {
        if (v == null || v.isBlank()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", field);
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
