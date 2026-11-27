package com.cim.mds.server.flow;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cim.mds.server.equipment.CimEquipment;
import com.cim.mds.server.equipment.CimEquipmentRepository;
import com.cim.mds.server.equipment.CimToolGroupMemberRepository;
import com.cim.mds.server.equipment.CimToolGroupRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 配方解算契约（V29，recipe-resolve-design.md §5）——MDS 对 WIP/MES 交付的
 * 「可校验、可追溯、可确定解算」的执行契约。
 *
 * <p>复用 {@link FlowAdminService#effectiveAll}（ContextChain 整层优先 + assign 池
 * priority 排序 + 产品轴）拿到节点的生效配方引用与候选池，再叠加本服务独有的
 * 四道闸：流程 ACTIVE 闸 → 机台覆盖闸（池行按 EQUIP/CLASS/GROUP 判覆盖）→
 * 确定性闸（同层同优先级配方口径不一致 = AMBIGUOUS_POOL）→ 可变参数闸
 * （覆盖合法性 + 防呆范围）。PPID/版本/hash/资格经 {@link RmsRecipeClient}
 * 调 RMS IF-M2 取真值——MDS 不存配方 Body 与映射。</p>
 *
 * <p>BLOCKED 语义：解算本身成功、答案是「阻断」——HTTP 200 + resolved=false +
 * 机器可读 reason（进站前硬阻断的原因码，供 MES 稳定消费）；入参缺失走常规
 * BizException（2000/2001）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeResolveService {

    private final FlowAdminService flowAdminService;
    private final CimFlowRepository flowRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final CimToolGroupRepository toolGroupRepository;
    private final CimToolGroupMemberRepository toolGroupMemberRepository;
    private final CimOperationRepository operationRepository;
    private final CimOperationVariableParamRepository varParamRepository;
    private final RmsRecipeClient rmsClient;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------ 请求 / 响应

    /** 解算请求。productCode 供 ContextChain 产品轴（可空=主数据口径）；overrides=本次覆盖值。 */
    public record ResolveReq(String productCode, String flowId, String nodeId,
                             String equipmentCode, List<ParamOverride> overrides) {
    }

    /** 单条运行时覆盖（Wafer/Lot 级参数覆盖）。 */
    public record ParamOverride(String paramKey, String value) {
    }

    /** 可变参数解算取值。 */
    public record VarValue(String paramKey, String value, String source,
                           String minValue, String maxValue, String overrideLevel) {
    }

    /** 解算快照（resolved=false 时仅 reason/reasonDetail/入参回显有值）。 */
    public record ResolveView(
            boolean resolved, String reason, String reasonDetail,
            String productCode, String flowId, String flowCode, String familyCode,
            Integer flowVersionNo, String flowStatus,
            String nodeId, String stepCode, String operationCode,
            String logicalRecipeRef, String logicalSource,
            String equipmentCode,
            String assignRowId, String assignScope, Integer assignPriority, String recipeRule,
            String ppid, String ppidSource,
            String chamberCode, String rmsVersionNo, String rmsVersionStatus, String bodyHash,
            List<VarValue> variableParams, LocalDateTime resolvedAt) {
    }

    /** 可变参数闸结果：reason != null = 阻断。 */
    private record VarResult(List<VarValue> vars, String reason, String detail) {

        static VarResult of(List<VarValue> vars) {
            return new VarResult(vars, null, null);
        }

        static VarResult blocked(String reason, String detail) {
            return new VarResult(List.of(), reason, detail);
        }
    }

    // ------------------------------------------------------------------ 解算

    @Transactional(readOnly = true)
    public ResolveView resolve(ResolveReq req) {
        if (req == null || !StringUtils.hasText(req.flowId())) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", "flowId");
        }
        if (!StringUtils.hasText(req.nodeId())) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", "nodeId");
        }
        if (!StringUtils.hasText(req.equipmentCode())) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", "equipmentCode");
        }
        String product = StringUtils.hasText(req.productCode()) ? req.productCode().trim() : null;
        String equipmentCode = req.equipmentCode().trim();

        // ① 流程闸：仅 ACTIVE 流程可解算（WIP 投批依据生效版本）
        CimFlow flow = flowRepository.findById(req.flowId().trim())
                .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.flow.notFound", req.flowId()));
        if (!CimFlow.STATUS_ACTIVE.equals(flow.getStatus())) {
            return blocked(req, product, flow, equipmentCode, "VERSION_NOT_ACTIVE",
                    "流程当前状态 " + flow.getStatus() + "（仅 ACTIVE 可解算）");
        }

        // ② 节点闸：nodeId 必须在流程内；候选池非空（复用 effectiveAll：含产品轴）
        List<ContextResolver.NodeEffective> nodes = flowAdminService.effectiveAll(flow.getId(), product);
        ContextResolver.NodeEffective node = nodes.stream()
                .filter(n -> n.nodeId().equals(req.nodeId().trim()))
                .findFirst()
                .orElse(null);
        if (node == null) {
            return blocked(req, product, flow, equipmentCode, "NODE_NOT_FOUND",
                    "节点 " + req.nodeId() + " 不在流程 " + flow.getFlowCode() + " 内");
        }
        if (node.pool().isEmpty()) {
            return blocked(req, product, flow, equipmentCode, "NO_CANDIDATE_POOL",
                    "节点 " + node.stepCode() + " 沿 ContextChain 各层均无机台指派");
        }

        // ③ 机台闸：池行覆盖判定（EQUIP=code 精确 / CLASS=机型 / GROUP=组员）
        CimEquipment equipment = equipmentRepository
                .findByEquipmentCodeAndDeletedFalse(equipmentCode)
                .filter(e -> Boolean.TRUE.equals(e.getEnabled()))
                .orElse(null);
        if (equipment == null) {
            return blocked(req, product, flow, equipmentCode, "RECIPE_NOT_AUTHORIZED",
                    "机台 " + equipmentCode + " 不存在或已停用");
        }
        List<CimEquipAssign> covering = node.pool().stream()
                .filter(a -> covers(a, equipment))
                .toList();
        if (covering.isEmpty()) {
            return blocked(req, product, flow, equipmentCode, "RECIPE_NOT_AUTHORIZED",
                    "机台 " + equipmentCode + " 不在节点 " + node.stepCode() + " 的授权候选池内");
        }

        // ④ 确定性闸：取覆盖行中 priority 最小层；同层配方口径不一致 = 歧义（全自动唯一解算红线）
        int topPriority = covering.stream()
                .mapToInt(a -> a.getPriority() == null ? 100 : a.getPriority())
                .min()
                .orElse(100);
        List<CimEquipAssign> top = covering.stream()
                .filter(a -> (a.getPriority() == null ? 100 : a.getPriority()) == topPriority)
                .toList();
        Set<String> outcomes = new LinkedHashSet<>();
        for (CimEquipAssign a : top) {
            outcomes.add(outcomeKey(a));
        }
        if (outcomes.size() > 1) {
            return blocked(req, product, flow, equipmentCode, "AMBIGUOUS_POOL",
                    "节点 " + node.stepCode() + " 在优先级 " + topPriority + " 上存在 "
                            + top.size() + " 条配方口径不一致的指派行: " + outcomes);
        }
        CimEquipAssign row = top.get(0);
        String rule = row.getRecipeRule() == null ? CimEquipAssign.RULE_FIXED : row.getRecipeRule();
        String logical = node.recipeRef().value();
        String logicalSrc = node.recipeRef().source();

        // ⑤ 配方规则：FIXED 直钉 PPID；GENERATE/EXACT_MATCH 命中后走 RMS IF-M2 取真值
        String ppid = null;
        String ppidSource = null;
        String chamberCode = null;
        String rmsVersionNo = null;
        String rmsVersionStatus = null;
        String bodyHash = null;
        if (CimEquipAssign.RULE_FIXED.equals(rule)) {
            if (!StringUtils.hasText(row.getRecipeRef())) {
                return blockedWith(req, product, flow, equipmentCode, node, row, topPriority, rule,
                        logical, logicalSrc, "RECIPE_REF_MISSING",
                        "指派行 [" + row.getAssignType() + ":" + row.getAssignRef() + "] FIXED 未填 recipe_ref");
            }
            ppid = row.getRecipeRef();
            ppidSource = "MDS_FIXED";
        } else if (CimEquipAssign.RULE_EXACT_MATCH.equals(rule)
                || CimEquipAssign.RULE_GENERATE.equals(rule)) {
            // EXACT_MATCH（v1 匹配域=本次 overrides）：recipe_match 键值对全部命中才放行
            if (CimEquipAssign.RULE_EXACT_MATCH.equals(rule)) {
                Map<String, String> want = parseMatch(row.getRecipeMatch());
                Map<String, String> got = overrideMap(req);
                List<String> missing = want.entrySet().stream()
                        .filter(e -> !e.getValue().equals(got.get(e.getKey())))
                        .map(Map.Entry::getKey)
                        .toList();
                if (!want.isEmpty() && !missing.isEmpty()) {
                    return blockedWith(req, product, flow, equipmentCode, node, row, topPriority, rule,
                            logical, logicalSrc, "EXACT_MATCH_UNRESOLVED",
                            "键值匹配未命中: " + missing);
                }
            }
            if (!StringUtils.hasText(logical)) {
                return blockedWith(req, product, flow, equipmentCode, node, row, topPriority, rule,
                        logical, logicalSrc, "RECIPE_REF_MISSING",
                        "节点 " + node.stepCode() + " 生效配方引用为空，无法实例化 PPID");
            }
            RmsRecipeClient.Lookup lk = rmsClient.lookup(equipmentCode, logical);
            if (!"RESOLVED".equals(lk.status())) {
                return blockedWith(req, product, flow, equipmentCode, node, row, topPriority, rule,
                        logical, logicalSrc, mapRmsStatus(lk.status()),
                        lk.reason() == null ? lk.status() : lk.reason());
            }
            ppid = lk.ppid();
            ppidSource = "RMS_GENERATED";
            chamberCode = lk.chamberCode();
            rmsVersionNo = lk.versionNo() == null ? null : String.valueOf(lk.versionNo());
            rmsVersionStatus = lk.versionStatus();
            bodyHash = lk.bodyHash();
        } else {
            return blockedWith(req, product, flow, equipmentCode, node, row, topPriority, rule,
                    logical, logicalSrc, "RECIPE_REF_MISSING", "未知 recipe_rule: " + rule);
        }

        // ⑥ 可变参数闸：覆盖合法性 + 防呆范围（默认值兜底）
        VarResult vars = resolveVarParams(node, req);
        if (vars.reason() != null) {
            return blockedWith(req, product, flow, equipmentCode, node, row, topPriority, rule,
                    logical, logicalSrc, vars.reason(), vars.detail());
        }

        return new ResolveView(true, null, null,
                product, flow.getId(), flow.getFlowCode(), flow.getFamilyCode(),
                flow.getVersionNo(), flow.getStatus(),
                node.nodeId(), node.stepCode(), node.operationCode(),
                logical, logicalSrc,
                equipmentCode,
                row.getId(), row.getScopeType(), topPriority, rule,
                ppid, ppidSource,
                chamberCode, rmsVersionNo, rmsVersionStatus, bodyHash,
                vars.vars(), LocalDateTime.now());
    }

    /**
     * 可变参数闸：与 ContextResolver 同口径取工序族 ACTIVE 版本行；
     * 未声明可变的参数被覆盖 = OVERRIDE_NOT_ALLOWED；NONE 层级被覆盖同口径；
     * min/max 两侧均可解析为数值时做范围校验（TEXT 语义跳过）。
     */
    private VarResult resolveVarParams(ContextResolver.NodeEffective node, ResolveReq req) {
        if (node.operationCode() == null || node.operationCode().isBlank()) {
            return VarResult.of(List.of());
        }
        CimOperation opRow = operationRepository
                .findByOpCodeAndDeletedFalse(node.operationCode()).orElse(null);
        if (opRow != null && StringUtils.hasText(opRow.getOpFamilyCode())) {
            opRow = operationRepository
                    .findByOpFamilyCodeAndDeletedFalseAndStatus(
                            opRow.getOpFamilyCode(), CimOperation.STATUS_ACTIVE)
                    .orElse(opRow);
        }
        if (opRow == null) {
            return VarResult.of(List.of());
        }
        Map<String, String> overrides = overrideMap(req);
        List<CimOperationVariableParam> declared = varParamRepository
                .findByOpIdAndDeletedFalseOrderBySortNoAscParamKeyAsc(opRow.getId());
        // 引用即契约：不在声明清单内的键不接受运行时改写
        for (String key : overrides.keySet()) {
            boolean declaredKey = declared.stream().anyMatch(d -> d.getParamKey().equals(key));
            if (!declaredKey) {
                return VarResult.blocked("OVERRIDE_NOT_ALLOWED",
                        "参数 " + key + " 未在工序 " + node.operationCode() + " 声明为可变参数");
            }
        }
        List<VarValue> out = new ArrayList<>();
        for (CimOperationVariableParam d : declared) {
            String ov = overrides.get(d.getParamKey());
            boolean isOverride = ov != null;
            if (isOverride && CimOperationVariableParam.LEVEL_NONE.equals(d.getOverrideLevel())) {
                return VarResult.blocked("OVERRIDE_NOT_ALLOWED",
                        "参数 " + d.getParamKey() + " 声明为不可覆盖（override_level=NONE）");
            }
            String value = isOverride ? ov : d.getDefaultValue();
            if (isOverride && StringUtils.hasText(d.getMinValue()) && StringUtils.hasText(d.getMaxValue())) {
                try {
                    double v = Double.parseDouble(value);
                    double min = Double.parseDouble(d.getMinValue());
                    double max = Double.parseDouble(d.getMaxValue());
                    if (v < min || v > max) {
                        return VarResult.blocked("OVERRIDE_OUT_OF_RANGE",
                                "参数 " + d.getParamKey() + " 覆盖值 " + v + " 越界 ["
                                        + d.getMinValue() + ", " + d.getMaxValue() + "]");
                    }
                } catch (NumberFormatException ignore) {
                    // 非数值语义不做范围比较——防呆范围仅对可解析值生效
                }
            }
            out.add(new VarValue(d.getParamKey(), value,
                    isOverride ? "OVERRIDE" : "DEFAULT",
                    d.getMinValue(), d.getMaxValue(), d.getOverrideLevel()));
        }
        return VarResult.of(out);
    }

    // ------------------------------------------------------------------ 工具

    /** 池行是否覆盖给定机台。 */
    private boolean covers(CimEquipAssign a, CimEquipment equipment) {
        return switch (a.getAssignType()) {
            case CimEquipAssign.TYPE_EQUIP -> equipment.getEquipmentCode().equals(a.getAssignRef());
            case CimEquipAssign.TYPE_CLASS -> a.getAssignRef() != null
                    && a.getAssignRef().equals(equipment.getClassCode());
            case CimEquipAssign.TYPE_GROUP -> toolGroupRepository
                    .findByGroupCodeAndDeletedFalse(a.getAssignRef())
                    .map(g -> toolGroupMemberRepository.findByGroupIdAndDeletedFalse(g.getId()).stream()
                            .anyMatch(m -> equipment.getId().equals(m.getEquipmentId())))
                    .orElse(false);
            default -> false;
        };
    }

    /** 配方口径键（确定性闸的比较粒度）。 */
    private String outcomeKey(CimEquipAssign a) {
        String rule = a.getRecipeRule() == null ? CimEquipAssign.RULE_FIXED : a.getRecipeRule();
        return rule + "|" + (a.getRecipeRef() == null ? "" : a.getRecipeRef())
                + "|" + (a.getRecipeMatch() == null ? "" : a.getRecipeMatch());
    }

    private Map<String, String> overrideMap(ResolveReq req) {
        Map<String, String> m = new LinkedHashMap<>();
        if (req.overrides() != null) {
            for (ParamOverride o : req.overrides()) {
                if (o != null && StringUtils.hasText(o.paramKey())) {
                    m.put(o.paramKey().trim(), o.value() == null ? "" : o.value());
                }
            }
        }
        return m;
    }

    private Map<String, String> parseMatch(String json) {
        Map<String, String> m = new LinkedHashMap<>();
        if (!StringUtils.hasText(json)) {
            return m;
        }
        try {
            var tree = objectMapper.readTree(json);
            tree.properties().forEach(e -> m.put(e.getKey(),
                    e.getValue().isNull() ? null : e.getValue().asText()));
        } catch (Exception e) {
            // JSON 合法性已由 validate RECIPE_RESOLVED 守门；此处宽松降级为空集
            log.warn("[recipe-resolve] recipe_match JSON 解析失败: {}", e.getMessage());
        }
        return m;
    }

    /** RMS status → MDS 原因码（RECIPE_REF_MISSING / RMS_UNAVAILABLE 本地合成，原样透传）。 */
    private String mapRmsStatus(String rmsStatus) {
        if (rmsStatus == null) {
            return "RMS_UNAVAILABLE";
        }
        return switch (rmsStatus) {
            case "RESOLVED", "RECIPE_REF_MISSING", "RMS_UNAVAILABLE" -> rmsStatus;
            case "DEVICE_NOT_FOUND", "DEVICE_DISABLED", "NOT_QUALIFIED" -> "RECIPE_NOT_AUTHORIZED";
            case "RECIPE_NOT_FOUND", "NO_ACTIVE_VERSION" -> "VERSION_NOT_ACTIVE";
            case "MAPPING_INACTIVE" -> "PPID_MAPPING_INACTIVE";
            default -> "RMS_UNAVAILABLE";
        };
    }

    private ResolveView blocked(ResolveReq req, String product, CimFlow flow, String equipmentCode,
                                String reason, String detail) {
        return new ResolveView(false, reason, detail,
                product,
                flow == null ? req.flowId() : flow.getId(),
                flow == null ? null : flow.getFlowCode(),
                flow == null ? null : flow.getFamilyCode(),
                flow == null ? null : flow.getVersionNo(),
                flow == null ? null : flow.getStatus(),
                StringUtils.hasText(req.nodeId()) ? req.nodeId().trim() : null,
                null, null,
                null, null,
                equipmentCode,
                null, null, null, null,
                null, null,
                null, null, null, null,
                List.of(), LocalDateTime.now());
    }

    /** 携带节点/指派行上下文的 BLOCKED（配方规则阶段；解算走到哪一步就回显到哪一步）。 */
    private ResolveView blockedWith(ResolveReq req, String product, CimFlow flow, String equipmentCode,
                                    ContextResolver.NodeEffective node, CimEquipAssign row, int priority,
                                    String rule, String logical, String logicalSrc,
                                    String reason, String detail) {
        return new ResolveView(false, reason, detail,
                product, flow.getId(), flow.getFlowCode(), flow.getFamilyCode(),
                flow.getVersionNo(), flow.getStatus(),
                node.nodeId(), node.stepCode(), node.operationCode(),
                logical, logicalSrc,
                equipmentCode,
                row.getId(), row.getScopeType(), priority, rule,
                null, null,
                null, null, null, null,
                List.of(), LocalDateTime.now());
    }
}
