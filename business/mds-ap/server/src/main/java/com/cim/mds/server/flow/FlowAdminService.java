package com.cim.mds.server.flow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cim.mds.server.equipment.CimEquipmentClassRepository;
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
    private final CimEquipmentClassRepository equipmentClassRepository;
    private final com.cim.mds.server.equipment.CimCapabilityRepository capabilityRepository;
    private final CimOperationRepository operationRepository;
    private final com.cim.mds.server.reticle.CimReticleRepository reticleRepository;
    private final NamingRuleAdminService namingRuleService;
    // ICM（V22）：约束校验 / 指派 / KV / 绑定 / 校验明细
    private final FlowValidateService flowValidateService;
    private final CimFlowValidateResultRepository validateResultRepository;
    private final CimEquipAssignRepository assignRepository;
    private final CimConstraintAttrRepository attrRepository;
    private final CimParamDefRepository paramDefRepository;
    private final CimProductFlowRepository productFlowRepository;
    // 事件历史（V24，P3-2）：动作级事件统一写入口
    private final FlowEventService flowEventService;
    // 实时通知（P3-3）：validate ERROR / 绑定变更 → 编辑锁占用者
    private final FlowNotifyService flowNotifyService;

    // ------------------------------------------------------------------ 视图 / 请求

    /** 流程主档视图（flowXml = 画布源文件原样回传，前端直接渲染）。 */
    public record FlowView(String id, String flowCode, String flowName, String techCode,
                           String status, String flowXml, String remark, String description,
                           String flowKind, int stepCount,
                           String familyCode, Integer versionNo,
                           String validateState, String validateMsg, String validateTime,
                           String stageCode, String reworkOfFamily, Integer reworkCountLimit,
                           Boolean isOccupied, String occupiedBy) {
    }

    /** 解析产物节点视图（stepNo = XML 原顺序； nodeId = 边引用锚点；operationCode = 引用工序库，V20；
     *  capabilityCode/edcPlanCode = 能力需求/EDC Plan 覆写（V21，可空 = 继承工序默认）；
     *  reticleRef/contamLevel/contamForb（V22 ICM 覆盖列，NULL=继承）。 */
    public record StepView(String id, String nodeId, String stepCode, String stepName,
                           String stepType, String operationCode, Integer stepNo, String recipeRef, String equipType,
                           Integer qtimeMin, Integer qtimeMax, Integer reworkLimit,
                           String flowRef, String batchMode, Integer batchSize,
                           Boolean holdPoint, String holdReason,
                           String inspectPlan, java.math.BigDecimal samplingRate,
                           String capabilityCode, String edcPlanCode,
                           String reticleRef, String contamLevel, String contamForbidden,
                           Integer npwLimit) {
    }

    /** 解析产物边视图（from/to = XML 节点 id）。 */
    public record EdgeView(String id, String fromStepId, String toStepId,
                           String edgeType, String conditionExpr) {
    }

    /** 跨站 Q-time 视图（from/to = XML 节点 id；alarmCode V23）。 */
    public record QtimeView(String id, String fromNodeId, String toNodeId,
                            Integer maxMinutes, String action, String alarmCode) {
    }

    /** 流程详情（主档含 XML + 全部解析产物；画布页一次拉全）。 */
    public record FlowDetail(FlowView flow, List<StepView> steps, List<EdgeView> edges,
                             List<QtimeView> qtimes) {
    }

    /**
     * 主档新建/更新请求。flowCode 可空：有启用规则留空自动占号、手填走正则守门；
     * 无启用规则时必须手填（与产品域语义一致）。flowKind MAIN/SUB（可空默认 MAIN）。
     * stageCode/reworkOfFamily/reworkCountLimit = 站点分组轴 / 返工流程标注 / 返工计数上限
     * （V23 主档编辑入口，拍板项 G「P2 填数据」）。
     */
    public record FlowReq(String flowCode, String flowName, String techCode,
                          String remark, String description, String flowKind,
                          String stageCode, String reworkOfFamily, Integer reworkCountLimit) {
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
        entity.setStageCode(blankToNull(req.stageCode()));
        entity.setReworkOfFamily(blankToNull(req.reworkOfFamily()));
        entity.setReworkCountLimit(req.reworkCountLimit());
        entity.setStatus(CimFlow.STATUS_UNFROZEN);
        entity.setFamilyCode(code); // 新建 = 自成版本族
        entity.setVersionNo(1);
        CimFlow saved = flowRepository.save(entity);
        flowEventService.record(saved.getId(), saved.getFlowCode(), FlowEventService.T_FLOW,
                "CREATE", FlowEventService.summary("flowName", saved.getFlowName(), "techCode", saved.getTechCode()));
        return toView(saved, 0);
    }

    /** 主档更新（只吃名称/技术/备注/种类；flowCode 与画布不在这里动）。 */
    @Transactional
    public FlowView update(String id, FlowReq req) {
        CimFlow entity = requireFlow(id);
        requireDraft(entity);
        requireNotLockedByOther(entity);
        requireText(req.flowName(), "flowName");
        entity.setFlowName(req.flowName().trim());
        entity.setTechCode(requireTech(req.techCode()));
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setFlowKind(requireFlowKind(req.flowKind()));
        entity.setStageCode(blankToNull(req.stageCode()));
        entity.setReworkOfFamily(blankToNull(req.reworkOfFamily()));
        entity.setReworkCountLimit(req.reworkCountLimit());
        flowEventService.record(id, entity.getFlowCode(), FlowEventService.T_FLOW,
                "UPDATE", FlowEventService.summary("flowName", entity.getFlowName(),
                        "stageCode", entity.getStageCode(), "reworkOfFamily", entity.getReworkOfFamily()));
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
        flowEventService.record(id, flow.getFlowCode(), FlowEventService.T_FLOW, "DELETE",
                FlowEventService.summary("status", flow.getStatus()));
        flowRepository.delete(flow);
    }

    // ------------------------------------------------------------------ ICM（V22）：编辑锁 / 工序刷新 / 解析

    /** 编辑锁判定：被他人占用即拒绝（锁管「人 vs 人」，四态管「版本生命周期」，两层独立）。 */
    private static void requireNotLockedByOther(CimFlow flow) {
        if (Boolean.TRUE.equals(flow.getIsOccupied())
                && flow.getOccupiedBy() != null
                && !flow.getOccupiedBy().equals(com.cim.mds.server.site.EditLockSupport.currentUser())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.lockedByOther", flow.getOccupiedBy());
        }
    }

    /** 锁定编辑（UNFROZEN 且未锁；占用后他人不可保存画布/改覆盖数据）。 */
    @Transactional
    public FlowView lock(String id) {
        CimFlow flow = requireFlow(id);
        requireDraft(flow);
        if (Boolean.TRUE.equals(flow.getIsOccupied())
                && !flow.getOccupiedBy().equals(com.cim.mds.server.site.EditLockSupport.currentUser())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.lockedByOther", flow.getOccupiedBy());
        }
        flow.setIsOccupied(Boolean.TRUE);
        flow.setOccupiedBy(com.cim.mds.server.site.EditLockSupport.currentUser());
        flow.setOccupiedAt(java.time.LocalDateTime.now());
        return toView(flowRepository.save(flow), countSteps(id));
    }

    /** 释放编辑锁（仅锁定人本人）。 */
    @Transactional
    public FlowView unlock(String id) {
        CimFlow flow = requireFlow(id);
        if (Boolean.TRUE.equals(flow.getIsOccupied())
                && !flow.getOccupiedBy().equals(com.cim.mds.server.site.EditLockSupport.currentUser())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.unlockByOther", flow.getOccupiedBy());
        }
        flow.setIsOccupied(Boolean.FALSE);
        flow.setOccupiedBy(null);
        flow.setOccupiedAt(null);
        return toView(flowRepository.save(flow), countSteps(id));
    }

    /** 刷新工序引用 diff（逐节点新旧 op_code）。 */
    public record OpRefreshDiff(String nodeId, String stepCode, String oldOpCode,
                                String newOpCode, boolean changed) {
    }

    /**
     * 「刷新工序引用」（仅 UNFROZEN + 未锁）：按 op_family_code 将各节点指到族内最新
     * ACTIVE 版本（WIP 稳定性 = 引用钉版本行，「跟随」由本显式动作承担）。请求体
     * stepIds 可空 = 全部节点。返回逐节点 diff。
     */
    @Transactional
    public List<OpRefreshDiff> refreshOperations(String id, List<String> stepIds) {
        CimFlow flow = requireFlow(id);
        requireDraft(flow);
        requireNotLockedByOther(flow);
        List<CimFlowStep> steps = stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(id);
        List<OpRefreshDiff> diffs = new ArrayList<>();
        for (CimFlowStep s : steps) {
            if (stepIds != null && !stepIds.isEmpty() && !stepIds.contains(s.getNodeId())) {
                continue;
            }
            if (s.getOperationCode() == null || s.getOperationCode().isBlank()) {
                continue;
            }
            CimOperation current = operationRepository
                    .findByOpCodeAndDeletedFalse(s.getOperationCode()).orElse(null);
            if (current == null || current.getOpFamilyCode() == null) {
                continue;
            }
            CimOperation latest = operationRepository
                    .findByOpFamilyCodeAndDeletedFalseAndStatus(
                            current.getOpFamilyCode(), CimOperation.STATUS_ACTIVE)
                    .filter(op -> !op.getOpCode().equals(current.getOpCode()))
                    .orElse(null);
            if (latest != null) {
                s.setOperationCode(latest.getOpCode());
                stepRepository.save(s);
                diffs.add(new OpRefreshDiff(s.getNodeId(), s.getStepCode(),
                        current.getOpCode(), latest.getOpCode(), true));
            } else {
                diffs.add(new OpRefreshDiff(s.getNodeId(), s.getStepCode(),
                        current.getOpCode(), current.getOpCode(), false));
            }
        }
        if (!diffs.isEmpty()) {
            flowEventService.record(id, flow.getFlowCode(), FlowEventService.T_STEP, "REFRESH_OP",
                    FlowEventService.summary("changed", diffs.stream().filter(OpRefreshDiff::changed).count(),
                            "scanned", diffs.size()));
        }
        return diffs;
    }

    /** 列表页轻量聚合：每流程启用业务节点数 + 缺指派节点数（避免列表页逐行调 effective）。 */
    public record StepFlags(String flowId, int businessCount, int unassignedCount) {
    }

    /** 批量 step-flags（flowIds 逗号分隔；多流程一次算完——批量解析防 N+1）。 */
    @Transactional(readOnly = true)
    public List<StepFlags> stepFlags(List<String> flowIds) {
        List<StepFlags> out = new ArrayList<>();
        for (String flowId : flowIds) {
            List<CimFlowStep> steps = stepRepository
                    .findByFlowIdAndDeletedFalseOrderByStepNoAsc(flowId);
            List<ContextResolver.NodeEffective> nodes = resolveEffective(flowId, steps);
            int business = 0;
            int unassigned = 0;
            for (ContextResolver.NodeEffective n : nodes) {
                boolean isBusiness = CimFlowStep.TYPE_PROCESS.equals(n.stepType())
                        || CimFlowStep.TYPE_INSPECT.equals(n.stepType())
                        || CimFlowStep.TYPE_REWORK.equals(n.stepType());
                if (isBusiness) {
                    business++;
                    if (n.pool().isEmpty()) {
                        unassigned++;
                    }
                }
            }
            out.add(new StepFlags(flowId, business, unassigned));
        }
        return out;
    }

    /** 单节点生效值（ICM 关键端点；预留 product 上下文 P3）。 */
    @Transactional(readOnly = true)
    public ContextResolver.NodeEffective effective(String flowId, String nodeId) {
        requireFlow(flowId);
        List<CimFlowStep> steps = stepRepository
                .findByFlowIdAndDeletedFalseOrderByStepNoAsc(flowId);
        List<ContextResolver.NodeEffective> all = resolveEffective(flowId, steps);
        return all.stream().filter(n -> n.nodeId().equals(nodeId)).findFirst()
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.assign.stepNotFound", nodeId));
    }

    /** 批量生效值（画布渲染/对比/Excel 共用，防逐节点 N+1）。 */
    @Transactional(readOnly = true)
    public List<ContextResolver.NodeEffective> effectiveAll(String flowId) {
        return effectiveAll(flowId, null);
    }

    /**
     * 批量生效值·按产品轴（V25 L3）：productCode 非空时启用 PRODUCT 层（绑定行列型覆盖 +
     * scope=PRODUCT 指派/KV），解析序 PRODUCT &gt; STEP &gt; FLOW &gt; OPERATION；
     * productCode 空 = 主数据口径（validate 同款，不带产品轴）。
     */
    @Transactional(readOnly = true)
    public List<ContextResolver.NodeEffective> effectiveAll(String flowId, String productCode) {
        requireFlow(flowId);
        List<CimFlowStep> steps = stepRepository
                .findByFlowIdAndDeletedFalseOrderByStepNoAsc(flowId);
        return resolveEffective(flowId, steps, productCode);
    }

    /** 组装 ResolveCtx 并解析（校验/flags/effective 共用；纯解析部分在 ContextResolver）。 */
    private List<ContextResolver.NodeEffective> resolveEffective(String flowId, List<CimFlowStep> steps) {
        return resolveEffective(flowId, steps, null);
    }

    private List<ContextResolver.NodeEffective> resolveEffective(String flowId, List<CimFlowStep> steps,
                                                                 String productCode) {
        CimFlow flow = flowRepository.findById(flowId).orElseThrow();
        Set<String> opCodes = new HashSet<>();
        steps.forEach(s -> {
            if (s.getOperationCode() != null && !s.getOperationCode().isBlank()) {
                opCodes.add(s.getOperationCode());
            }
        });
        Map<String, CimOperation> operations = new HashMap<>();
        for (String code : opCodes) {
            operationRepository.findByOpCodeAndDeletedFalse(code).ifPresent(op -> operations.put(code, op));
        }
        Set<String> families = new HashSet<>();
        operations.values().forEach(op -> {
            if (op.getOpFamilyCode() != null) {
                families.add(op.getOpFamilyCode());
            }
        });
        for (String family : families) {
            operationRepository.findByOpFamilyCodeAndDeletedFalseAndStatus(
                    family, CimOperation.STATUS_ACTIVE).ifPresent(op -> operations.put(op.getOpCode(), op));
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
        Map<String, CimParamDef> defs = new HashMap<>();
        paramDefRepository.findByDeletedFalseOrderBySortNoAsc().forEach(d -> defs.put(d.getAttrKey(), d));
        // V25 L3：产品轴（productCode 非空时挂绑定行 + PRODUCT 指派/KV）
        CimProductFlow binding = null;
        List<CimEquipAssign> productAssigns = List.of();
        List<CimConstraintAttr> productAttrs = List.of();
        if (productCode != null && !productCode.isBlank()) {
            String family = flow.getFamilyCode() == null ? flow.getFlowCode() : flow.getFamilyCode();
            binding = productFlowRepository.findByProductCodeAndFlowFamilyCodeAndBindStateAndDeletedFalse(
                    productCode.trim(), family, CimProductFlow.BIND_ACTIVE).orElse(null);
            productAssigns = assignRepository.findByScopeTypeAndProductCodeAndFamilyCodeAndDeletedFalse(
                    CimEquipAssign.SCOPE_PRODUCT, productCode.trim(), family);
            productAttrs = attrRepository.findByContextTypeAndProductCodeAndFamilyCodeAndDeletedFalse(
                    CimEquipAssign.SCOPE_PRODUCT, productCode.trim(), family);
        }
        return ContextResolver.resolveAll(new ContextResolver.ResolveCtx(
                flow, steps, operations, assigns, attrs, defs, binding, productAssigns, productAttrs));
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
        requireNotLockedByOther(flow); // ICM V22：编辑锁（人 vs 人）——画布 XML 整份回传覆盖是并发最高危点
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
                    && equipmentClassRepository.findByClassCodeAndDeletedFalse(n.equipType()).isEmpty()) {
                throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.flow.equipTypeNotFound", n.equipType());
            }
            // V21：能力需求非空才校验存在（AMAT 口径——经设备能力关联匹配可加工设备）
            if (n.capabilityCode() != null
                    && capabilityRepository.findByCapabilityCodeAndDeletedFalse(n.capabilityCode()).isEmpty()) {
                throw BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.capabilityNotFound", n.capabilityCode(), n.stepCode());
            }
            // V32：光罩引用非空才校验存在（引用闭环——cim_reticle 先行建，节点挂接）
            if (n.reticleRef() != null
                    && reticleRepository.findByReticleCodeAndDeletedFalse(n.reticleRef().trim()).isEmpty()) {
                throw BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.reticleNotFound", n.reticleRef(), n.stepCode());
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
            // NPW 投片上限（V23）：可空 = 不限；非空须 >0
            if (n.npwLimit() != null) {
                Integer npw = parseNullableInt(n.npwLimit(), "npwLimit", n.stepCode());
                if (npw != null && npw <= 0) {
                    throw BizException.of(BizCode.PARAM_INVALID,
                            "mds.biz.flow.npwLimitPositive", n.stepCode(), npw);
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
            entity.setReticleRef(n.reticleRef());
            entity.setContamLevel(n.contamLevel());
            entity.setContamForbidden(n.contamForbidden());
            entity.setNpwLimit(parseNullableInt(n.npwLimit(), "npwLimit", n.stepCode()));
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
            entity.setAlarmCode(q.alarmCode());
            qtimeRepository.save(entity);
        }
        // ICM（V22）：本次保存中消失的节点 → 级联删 STEP 级 assign/attr（事务内，防孤儿行）
        Set<String> savedNodeIds = new HashSet<>();
        nodes.forEach(n -> savedNodeIds.add(n.id()));
        assignRepository.findByScopeTypeAndFlowIdAndDeletedFalse(CimEquipAssign.SCOPE_STEP, id)
                .stream().filter(a -> !savedNodeIds.contains(a.getStepId()))
                .forEach(assignRepository::delete);
        attrRepository.findByContextTypeAndFlowIdAndDeletedFalse(CimEquipAssign.SCOPE_STEP, id)
                .stream().filter(a -> !savedNodeIds.contains(a.getStepId()))
                .forEach(attrRepository::delete);
        flowEventService.record(id, flow.getFlowCode(), FlowEventService.T_FLOW, "SAVE_XML",
                FlowEventService.summary("nodes", nodes.size(), "edges", edges.size(),
                        "qtimes", doc.qtimes().size()));
        return detail(id);
    }

    /**
     * 直线流程生成请求：工序列（顺序即画布顺序；每工序生成一个 PROCESS 节点，START/END 自动补）。
     */
    public record GenerateDesignReq(java.util.List<String> operationCodes) {
    }

    /**
     * generateDesign（P2，roadmap）：按工序列自动生成直线流程（START → N×PROCESS → END，
     * MAIN 全链直线）。节点 operation_code 带出（equipType/recipeRef 随画布属性从工序默认带出，
     * 即 ICM 的 L0 继承起点），后续差异在节点上局部覆盖。保存走 {@link #saveXml} 全量守门。
     */
    @Transactional
    public FlowDetail generateDesign(String id, GenerateDesignReq req) {
        requireFlow(id);
        if (req.operationCodes() == null || req.operationCodes().isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.flow.generateOpsRequired");
        }
        List<CimOperation> ops = new ArrayList<>();
        for (String code : req.operationCodes()) {
            if (code == null || code.isBlank()) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.flow.generateOpsRequired");
            }
            CimOperation op = operationRepository.findByOpCodeAndDeletedFalse(code.trim())
                    .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                            "mds.biz.flow.opRefNotFound", code.trim()));
            if (!CimOperation.STATUS_ACTIVE.equals(op.getStatus())) {
                throw BizException.of(BizCode.PARAM_INVALID,
                        "mds.biz.flow.opRefNotActive", op.getOpCode(), op.getStatus());
            }
            ops.add(op);
        }
        return saveXml(id, new CanvasReq(buildLinearCanvasXml(ops)));
    }

    /** 直线画布 XML 组装（generateDesign / Excel 导入共用内核；字段均须 XML 转义）。 */
    private String buildLinearCanvasXml(List<CimOperation> ops) {
        // 节点步进：前端画布节点宽 168（FlowPage x6 body），间距须 ≥ 节点宽 + 视觉留白，
        // 否则连线箭头叠在下一节点上（曾用 140 导致重叠）。
        final int xStep = 260;
        StringBuilder sb = new StringBuilder(256 + ops.size() * 256);
        sb.append("<canvas version=\"1\"><nodes>");
        int x = 40;
        sb.append(nodeXml("n1", "START", "开始", CimFlowStep.TYPE_START, null, x, 200));
        x += xStep;
        for (int i = 0; i < ops.size(); i++) {
            CimOperation op = ops.get(i);
            sb.append(nodeXml("n" + (i + 2), String.format("STP%03d", i + 1),
                    op.getOpName(), CimFlowStep.TYPE_PROCESS, op, x, 200));
            x += xStep;
        }
        sb.append(nodeXml("n" + (ops.size() + 2), "END", "结束", CimFlowStep.TYPE_END, null, x, 200));
        sb.append("</nodes><edges>");
        for (int i = 1; i <= ops.size() + 1; i++) {
            sb.append("<edge id=\"e").append(i).append("\" from=\"n").append(i)
                    .append("\" to=\"n").append(i + 1).append("\" edgeType=\"MAIN\"/>");
        }
        sb.append("</edges></canvas>");
        return sb.toString();
    }

    /** 单节点 XML（空属性按画布契约写空串；stepName 为空用工序名兜底）。 */
    private String nodeXml(String id, String stepCode, String stepName, String stepType,
                           CimOperation op, int x, int y) {
        String name = stepName == null || stepName.isBlank() ? "" : xmlEsc(stepName);
        StringBuilder sb = new StringBuilder(256);
        sb.append("<node id=\"").append(id).append("\" stepCode=\"").append(xmlEsc(stepCode))
                .append("\" stepName=\"").append(name).append("\" stepType=\"").append(stepType).append('"');
        if (op != null) {
            sb.append(" operationCode=\"").append(xmlEsc(op.getOpCode())).append('"');
            if (op.getRecipeRef() != null) {
                sb.append(" recipeRef=\"").append(xmlEsc(op.getRecipeRef())).append('"');
            }
            if (op.getEquipType() != null) {
                sb.append(" equipType=\"").append(xmlEsc(op.getEquipType())).append('"');
            }
            if (op.getQtimeMin() != null) {
                sb.append(" qtimeMin=\"").append(op.getQtimeMin()).append('"');
            }
            if (op.getQtimeMax() != null) {
                sb.append(" qtimeMax=\"").append(op.getQtimeMax()).append('"');
            }
            if (op.getReticleRef() != null) {
                sb.append(" reticleRef=\"").append(xmlEsc(op.getReticleRef())).append('"');
            }
        }
        sb.append(" x=\"").append(x).append("\" y=\"").append(y).append("\"/>");
        return sb.toString();
    }

    /** XML 属性转义（& < > "）。 */
    private static String xmlEsc(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------ 校验 / 生命周期

    /**
     * 全量校验并落库（ICM V22）：图结构规则（validateGraph 规则 1-8）+ ICM 约束规则
     * （FlowValidateService：指派/KV/配方/光罩/污染/工序引用，全部消费 ContextResolver
     * 解析值）。落 validate_state（ERROR≥1 → INVALID；否则 VALID）+ validate_msg 摘要
     * + validate_time/user，明细全量替换 cim_flow_validate_result。
     */
    @Transactional
    public ValidationResult validate(String id) {
        CimFlow flow = requireFlow(id);
        ValidationResult graph = validateGraph(id);
        List<FlowValidateService.Detail> details =
                flowValidateService.runRules(flow, graph.problems());
        // 落库：全量替换明细 + 主档校验态
        validateResultRepository.findByFlowIdAndDeletedFalse(id)
                .forEach(validateResultRepository::delete);
        validateResultRepository.flush();
        for (FlowValidateService.Detail d : details) {
            CimFlowValidateResult row = new CimFlowValidateResult();
            row.setFlowId(id);
            row.setStepId(d.stepNodeId());
            row.setRuleCode(d.ruleCode());
            row.setSeverity(d.severity());
            row.setMessage(d.message().length() > 512 ? d.message().substring(0, 512) : d.message());
            validateResultRepository.save(row);
        }
        String state = FlowValidateService.stateOf(details);
        flow.setValidateState(state);
        flow.setValidateMsg(FlowValidateService.summaryOf(details));
        flow.setValidateTime(java.time.LocalDateTime.now());
        flow.setValidateUser(com.cim.mds.server.site.EditLockSupport.currentUser());
        flowRepository.save(flow);
        // P3-3：validate 出 ERROR（INVALID）→ 通知编辑锁占用者（灰度开关默认关）
        if (!CimFlow.VS_VALID.equals(state)) {
            flowNotifyService.validateFailed(flow,
                    details.stream().filter(d -> CimFlowValidateResult.SEVERITY_ERROR.equals(d.severity())).count(),
                    com.cim.mds.server.site.EditLockSupport.currentUser());
        }
        return new ValidationResult(CimFlow.VS_VALID.equals(state),
                details.stream().map(d -> "[" + d.severity() + "/" + d.ruleCode() + "] " + d.message())
                        .toList());
    }

    /** 校验明细（报告页；validate 之后调用）。 */
    @Transactional(readOnly = true)
    public List<CimFlowValidateResult> validateResult(String id) {
        requireFlow(id);
        return validateResultRepository.findByFlowIdAndDeletedFalseOrderBySeverityAsc(id);
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
        flowEventService.record(id, flow.getFlowCode(), FlowEventService.T_FLOW, "FREEZE",
                FlowEventService.summary("validateState", flow.getValidateState()));
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
        flowEventService.record(id, flow.getFlowCode(), FlowEventService.T_FLOW, "UNFREEZE", null);
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
        // ICM 生效硬闸（V22）：validate_state 必须 VALID（2004 拒绝）——先校验再激活
        if (!CimFlow.VS_VALID.equals(flow.getValidateState())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.activateRequiresValid",
                    flow.getValidateState() + (flow.getValidateMsg() == null ? "" : " " + flow.getValidateMsg()));
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
        flowEventService.record(id, flow.getFlowCode(), FlowEventService.T_FLOW, "ACTIVATE",
                FlowEventService.summary("family", family, "versionNo", flow.getVersionNo()));
        return toView(flowRepository.save(flow), countSteps(id));
    }

    /** 禁用：FROZEN / ACTIVE → DISABLED（淘汰，不再允许任何使用）；有 ACTIVE 产品绑定 → 2004 先解绑。 */
    @Transactional
    public FlowView disable(String id) {
        CimFlow flow = requireFlow(id);
        if (!CimFlow.STATUS_FROZEN.equals(flow.getStatus())
                && !CimFlow.STATUS_ACTIVE.equals(flow.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.onlyFrozenOrActiveDisablable", flow.getStatus());
        }
        // ICM（V22）：防悬挂绑定——存在 ACTIVE 产品绑定须先解绑
        String family = flow.getFamilyCode() == null ? flow.getFlowCode() : flow.getFamilyCode();
        if (productFlowRepository.existsByFlowFamilyCodeAndBindStateAndDeletedFalse(
                family, CimProductFlow.BIND_ACTIVE)) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.disableHasActiveBinding", family);
        }
        flow.setStatus(CimFlow.STATUS_DISABLED);
        flowEventService.record(id, flow.getFlowCode(), FlowEventService.T_FLOW, "DISABLE", null);
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
        entity.setStageCode(source.getStageCode());
        entity.setReworkOfFamily(source.getReworkOfFamily());
        entity.setReworkCountLimit(source.getReworkCountLimit());
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
            clone.setReticleRef(s.getReticleRef());
            clone.setContamLevel(s.getContamLevel());
            clone.setContamForbidden(s.getContamForbidden());
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
            clone.setAlarmCode(q.getAlarmCode());
            qtimeRepository.save(clone);
        }
        // ICM（V22）复制搬运：scope=FLOW / scope=STEP 的 assign 行全量复制（node_id 原样克隆，
        // FLOW 级按新 flow_id 重挂）；scope=OPERATION 行不搬（模板共享，改工序默认全体受益）。
        // rule 不自动复制（V15 显式维护口径不变）。
        for (CimEquipAssign a : assignRepository.findByScopeTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_FLOW, id)) {
            CimEquipAssign clone = cloneAssign(a);
            clone.setFlowId(newId);
            assignRepository.save(clone);
        }
        for (CimEquipAssign a : assignRepository.findByScopeTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_STEP, id)) {
            CimEquipAssign clone = cloneAssign(a);
            clone.setFlowId(newId); // step_id（node_id）原样——XML/解析产物克隆时 node_id 保持
            assignRepository.save(clone);
        }
        for (CimConstraintAttr a : attrRepository.findByContextTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_FLOW, id)) {
            CimConstraintAttr clone = cloneAttr(a);
            clone.setFlowId(newId);
            attrRepository.save(clone);
        }
        for (CimConstraintAttr a : attrRepository.findByContextTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_STEP, id)) {
            CimConstraintAttr clone = cloneAttr(a);
            clone.setFlowId(newId);
            attrRepository.save(clone);
        }
        flowEventService.record(newId, entity.getFlowCode(), FlowEventService.T_FLOW, "COPY",
                FlowEventService.summary("fromFlowCode", source.getFlowCode(),
                        "fromFlowId", id, "versionNo", entity.getVersionNo()));
        return toView(entity, countSteps(newId));
    }

    private static CimEquipAssign cloneAssign(CimEquipAssign a) {
        CimEquipAssign c = new CimEquipAssign();
        c.setScopeType(a.getScopeType());
        c.setOperationCode(a.getOperationCode());
        c.setStepId(a.getStepId());
        c.setAssignType(a.getAssignType());
        c.setAssignRef(a.getAssignRef());
        c.setChamberRule(a.getChamberRule());
        c.setRecipeRef(a.getRecipeRef());
        c.setRecipeRule(a.getRecipeRule());
        c.setRecipeMatch(a.getRecipeMatch());
        c.setPriority(a.getPriority());
        c.setEnabled(a.getEnabled());
        c.setRemark(a.getRemark());
        c.setDescription(a.getDescription());
        return c;
    }

    private static CimConstraintAttr cloneAttr(CimConstraintAttr a) {
        CimConstraintAttr c = new CimConstraintAttr();
        c.setContextType(a.getContextType());
        c.setOperationCode(a.getOperationCode());
        c.setStepId(a.getStepId());
        c.setAttrKey(a.getAttrKey());
        c.setAttrValue(a.getAttrValue());
        c.setDescription(a.getDescription());
        return c;
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
                f.getVersionNo() == null ? 1 : f.getVersionNo(),
                f.getValidateState(), f.getValidateMsg(),
                f.getValidateTime() == null ? null : f.getValidateTime().toString(),
                f.getStageCode(), f.getReworkOfFamily(), f.getReworkCountLimit(),
                f.getIsOccupied(), f.getOccupiedBy());
    }

    private static StepView toStepView(CimFlowStep s) {
        return new StepView(s.getId(), s.getNodeId(), s.getStepCode(), s.getStepName(),
                s.getStepType(), s.getOperationCode(), s.getStepNo(), s.getRecipeRef(), s.getEquipType(),
                s.getQtimeMin(), s.getQtimeMax(), s.getReworkLimit(),
                s.getFlowRef(), s.getBatchMode(), s.getBatchSize(),
                s.getHoldPoint(), s.getHoldReason(),
                s.getInspectPlan(), s.getSamplingRate(),
                s.getCapabilityCode(), s.getEdcPlanCode(),
                s.getReticleRef(), s.getContamLevel(), s.getContamForbidden(),
                s.getNpwLimit());
    }

    private static QtimeView toQtimeView(CimFlowQtime q) {
        return new QtimeView(q.getId(), q.getFromNodeId(), q.getToNodeId(),
                q.getMaxMinutes(), q.getAction(), q.getAlarmCode());
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
