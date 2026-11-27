package com.cim.mds.server.flow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ContextResolver——ICM ContextChain 解析<b>单一事实源</b>（纯函数，无 IO，可安全批量）。
 *
 * <p><b>ContextChain</b>：L0 OPERATION 工序默认 → L1 FLOW 流程覆盖 → L2 STEP 节点覆盖
 * （P3 预留 PRODUCT）。解析顺序（特异→一般）：STEP &gt; FLOW &gt; OPERATION；每字段
 * 标注来源（{@link #SRC_STEP} / {@link #SRC_FLOW} / {@link #SRC_OP} / {@link #SRC_MISSING}），
 * 即 UI 的四级来源标注（节点覆盖蓝 / 流程覆盖青 / 工序默认灰 / 缺失红）。</p>
 *
 * <p><b>四链路共用（不允许各处各算）</b>：校验（FlowValidateService）、对比
 * （FlowCompareService）、Excel 导出（P2）、画布参数面板（GET /flows/{id}/effective）。</p>
 *
 * <p>解析语义三要点：① operation 按 {@code step.operation_code} <b>钉死版本行</b>解析
 * （已生效流程不受工序升版影响——WIP 稳定性）；② 机台候选池 = assign 行按层取最特异
 * 非空层（STEP 层有行整层生效，不与 FLOW 层合并），再 ∩ 资格门（equip_type）；
 * ③ 列型约束只在 OPERATION/STEP 两级（FLOW 级只开放指派与 KV）。</p>
 */
public final class ContextResolver {

    // ---------------- 来源层级（UI 五级标注，V25 加 PRODUCT） ----------------
    public static final String SRC_PRODUCT = "PRODUCT_OVERRIDE";
    public static final String SRC_STEP = "STEP_OVERRIDE";
    public static final String SRC_FLOW = "FLOW_OVERRIDE";
    public static final String SRC_OP = "OPERATION_DEFAULT";
    public static final String SRC_MISSING = "MISSING";

    private ContextResolver() {
    }

    /** 单字段生效值（value + 来源层级）。 */
    public record Effective<T>(T value, String source) {

        public static <T> Effective<T> of(T v, String src) {
            return new Effective<>(v, src);
        }

        public boolean missing() {
            return SRC_MISSING.equals(source);
        }
    }

    /** KV 生效值（attr_value + 来源 + 定义快照）。 */
    public record AttrEffective(String attrKey, String value, String source,
                                CimParamDef def) {
    }

    /**
     * 单节点解析结果。pool = 候选池行集（最特异非空层的全部启用行，已按 priority 升序）；
     * poolSource = 候选池来源层（无行 = MISSING）。opRefStale = 工序族内有更新的 ACTIVE
     * 版本（OP_REF_STALE WARN 提示「刷新工序引用」）。
     */
    public record NodeEffective(String nodeId, String stepCode, String stepName, String stepType,
                                String operationCode, String opStatus, boolean opRefStale,
                                Effective<String> recipeRef,
                                Effective<String> equipType,
                                Effective<Integer> qtimeMin, Effective<Integer> qtimeMax,
                                Effective<Integer> reworkLimit,
                                Effective<String> batchMode, Effective<Integer> batchSize,
                                Effective<Boolean> holdPoint, Effective<String> holdReason,
                                Effective<String> inspectPlan, Effective<BigDecimal> samplingRate,
                                Effective<String> reticleRef,
                                Effective<String> contamLevel, Effective<String> contamForbidden,
                                Effective<String> capabilityCode, Effective<String> edcPlanCode,
                                Effective<Integer> npwLimit,
                                String poolSource, List<CimEquipAssign> pool,
                                Map<String, AttrEffective> attrs) {
    }

    /**
     * 解析输入快照（调用方从 repository 组装；纯函数不做任何查询）。
     *
     * @param flow      流程主档
     * @param steps     解析产物节点（本流程）
     * @param operations 全量工序快照（本流程节点引用到的 op_code 对应行；缺行 = 引用悬空）
     * @param assigns   本流程相关的全部指派行（scope=FLOW 按 flow_id；scope=STEP 按 flow_id；
     *                  scope=OPERATION 按节点引用的 op_code 集合）
     * @param attrs     本流程相关的全部 KV 行（context 四层同上）
     * @param paramDefs 参数定义快照（key → def）
     * @param productBinding  产品绑定行（V25 L3；null = 无产品轴——validate 主数据口径不带产品）
     * @param productAssigns  scope=PRODUCT 指派行（product_code+family_code 匹配本产品/族；无产品轴传空表）
     * @param productAttrs    context=PRODUCT KV 行（同上）
     */
    public record ResolveCtx(CimFlow flow, List<CimFlowStep> steps,
                             Map<String, CimOperation> operations,
                             List<CimEquipAssign> assigns,
                             List<CimConstraintAttr> attrs,
                             Map<String, CimParamDef> paramDefs,
                             CimProductFlow productBinding,
                             List<CimEquipAssign> productAssigns,
                             List<CimConstraintAttr> productAttrs) {

        /** 三层便捷构造（无产品轴——validate/对比/画布主数据口径）。 */
        public ResolveCtx(CimFlow flow, List<CimFlowStep> steps,
                          Map<String, CimOperation> operations,
                          List<CimEquipAssign> assigns,
                          List<CimConstraintAttr> attrs,
                          Map<String, CimParamDef> paramDefs) {
            this(flow, steps, operations, assigns, attrs, paramDefs, null, List.of(), List.of());
        }
    }

    /** 全节点批量解析（画布渲染/对比/校验/按产品预览共用；顺序 = step_no 升序输入顺序）。 */
    public static List<NodeEffective> resolveAll(ResolveCtx ctx) {
        List<NodeEffective> out = new ArrayList<>();
        String flowId = ctx.flow().getId();
        // 分桶：FLOW 级 assign/attr 一批；STEP 级按 nodeId 分桶；OPERATION 级按 opCode 分桶；
        // PRODUCT 级（V25 L3）按 step_id 是否空分「整条流程 / 细化到站」两批
        List<CimEquipAssign> flowAssigns = new ArrayList<>();
        Map<String, List<CimEquipAssign>> stepAssigns = new HashMap<>();
        Map<String, List<CimEquipAssign>> opAssigns = new HashMap<>();
        List<CimEquipAssign> productFlowAssigns = new ArrayList<>();
        Map<String, List<CimEquipAssign>> productStepAssigns = new HashMap<>();
        for (CimEquipAssign a : ctx.assigns()) {
            if (Boolean.FALSE.equals(a.getEnabled())) {
                continue;
            }
            if (CimEquipAssign.SCOPE_FLOW.equals(a.getScopeType())) {
                flowAssigns.add(a);
            } else if (CimEquipAssign.SCOPE_STEP.equals(a.getScopeType())) {
                stepAssigns.computeIfAbsent(a.getStepId(), k -> new ArrayList<>()).add(a);
            } else if (CimEquipAssign.SCOPE_OPERATION.equals(a.getScopeType())) {
                opAssigns.computeIfAbsent(a.getOperationCode(), k -> new ArrayList<>()).add(a);
            }
        }
        for (CimEquipAssign a : ctx.productAssigns()) {
            if (Boolean.FALSE.equals(a.getEnabled())) {
                continue;
            }
            if (a.getStepId() != null && !a.getStepId().isBlank()) {
                productStepAssigns.computeIfAbsent(a.getStepId(), k -> new ArrayList<>()).add(a);
            } else {
                productFlowAssigns.add(a);
            }
        }
        Map<String, List<CimConstraintAttr>> stepAttrs = new HashMap<>();
        Map<String, List<CimConstraintAttr>> opAttrs = new HashMap<>();
        List<CimConstraintAttr> flowAttrs = new ArrayList<>();
        List<CimConstraintAttr> productFlowAttrs = new ArrayList<>();
        Map<String, List<CimConstraintAttr>> productStepAttrs = new HashMap<>();
        for (CimConstraintAttr a : ctx.attrs()) {
            if (CimConstraintAttrScope.isFlow(a)) {
                flowAttrs.add(a);
            } else if (CimConstraintAttrScope.isStep(a)) {
                stepAttrs.computeIfAbsent(a.getStepId(), k -> new ArrayList<>()).add(a);
            } else if (CimConstraintAttrScope.isOperation(a)) {
                opAttrs.computeIfAbsent(a.getOperationCode(), k -> new ArrayList<>()).add(a);
            }
        }
        for (CimConstraintAttr a : ctx.productAttrs()) {
            if (a.getStepId() != null && !a.getStepId().isBlank()) {
                productStepAttrs.computeIfAbsent(a.getStepId(), k -> new ArrayList<>()).add(a);
            } else {
                productFlowAttrs.add(a);
            }
        }
        // 工序族内最新 ACTIVE（OP_REF_STALE 判据：族内存在另一条 ACTIVE op_code ≠ 当前引用）
        Map<String, String> familyActiveOp = new HashMap<>();
        for (CimOperation op : ctx.operations().values()) {
            if (CimOperation.STATUS_ACTIVE.equals(op.getStatus()) && op.getOpFamilyCode() != null) {
                familyActiveOp.put(op.getOpFamilyCode(), op.getOpCode());
            }
        }
        for (CimFlowStep s : ctx.steps()) {
            CimOperation op = s.getOperationCode() == null ? null
                    : ctx.operations().get(s.getOperationCode());
            boolean stale = op != null && op.getOpFamilyCode() != null
                    && familyActiveOp.containsKey(op.getOpFamilyCode())
                    && !familyActiveOp.get(op.getOpFamilyCode()).equals(op.getOpCode());
            out.add(resolveOne(ctx, s, op, stale,
                    flowAssigns, stepAssigns.getOrDefault(s.getNodeId(), List.of()),
                    opAssigns.getOrDefault(s.getOperationCode(), List.of()),
                    productFlowAssigns, productStepAssigns.getOrDefault(s.getNodeId(), List.of()),
                    flowAttrs, stepAttrs.getOrDefault(s.getNodeId(), List.of()),
                    opAttrs.getOrDefault(s.getOperationCode(), List.of()),
                    productFlowAttrs, productStepAttrs.getOrDefault(s.getNodeId(), List.of())));
        }
        return out;
    }

    /** 单节点解析（private；上表所有字段沿链取最特异层：PRODUCT > STEP > FLOW > OPERATION）。 */
    private static NodeEffective resolveOne(ResolveCtx ctx, CimFlowStep s, CimOperation op,
                                            boolean stale,
                                            List<CimEquipAssign> flowAssigns,
                                            List<CimEquipAssign> stepAssigns,
                                            List<CimEquipAssign> opAssigns,
                                            List<CimEquipAssign> productFlowAssigns,
                                            List<CimEquipAssign> productStepAssigns,
                                            List<CimConstraintAttr> flowAttrs,
                                            List<CimConstraintAttr> stepAttrs,
                                            List<CimConstraintAttr> opAttrs,
                                            List<CimConstraintAttr> productFlowAttrs,
                                            List<CimConstraintAttr> productStepAttrs) {
        // 候选池：PRODUCT+STEP 行整层生效 → PRODUCT 行 → STEP 行 → FLOW 行 → OPERATION 行；空 = MISSING
        List<CimEquipAssign> pool;
        String poolSource;
        if (!productStepAssigns.isEmpty()) {
            pool = productStepAssigns;
            poolSource = SRC_PRODUCT;
        } else if (!productFlowAssigns.isEmpty()) {
            pool = productFlowAssigns;
            poolSource = SRC_PRODUCT;
        } else if (!stepAssigns.isEmpty()) {
            pool = stepAssigns;
            poolSource = SRC_STEP;
        } else if (!flowAssigns.isEmpty()) {
            pool = flowAssigns;
            poolSource = SRC_FLOW;
        } else if (!opAssigns.isEmpty()) {
            pool = opAssigns;
            poolSource = SRC_OP;
        } else {
            pool = List.of();
            poolSource = SRC_MISSING;
        }
        List<CimEquipAssign> sortedPool = new ArrayList<>(pool);
        sortedPool.sort(java.util.Comparator.comparingInt(a ->
                a.getPriority() == null ? 100 : a.getPriority()));

        Map<String, AttrEffective> attrs = new java.util.LinkedHashMap<>();
        // KV：每个 key 沿链取最特异层（PRODUCT → STEP → FLOW → OPERATION）
        Map<String, String> productKV = kv(productFlowAttrs, productStepAttrs, s.getNodeId());
        Map<String, String> stepKV = kv(stepAttrs);
        Map<String, String> flowKV = kv(flowAttrs);
        Map<String, String> opKV = kv(opAttrs);
        Set<String> keys = new HashSet<>();
        // 已注册参数定义全量进 attrs（未设值 = MISSING 来源 + def 快照）——
        // effective/校验(PARAM_KEY_DEFINED)/对比共用同一 key 域口径
        keys.addAll(ctx.paramDefs().keySet());
        keys.addAll(productKV.keySet());
        keys.addAll(stepKV.keySet());
        keys.addAll(flowKV.keySet());
        keys.addAll(opKV.keySet());
        for (String key : keys) {
            String v = productKV.containsKey(key) ? productKV.get(key) : null;
            String src = SRC_PRODUCT;
            if (v == null) {
                v = stepKV.get(key);
                src = SRC_STEP;
            }
            if (v == null) {
                v = flowKV.get(key);
                src = SRC_FLOW;
            }
            if (v == null) {
                v = opKV.get(key);
                src = SRC_OP;
            }
            if (v == null) {
                src = SRC_MISSING;
            }
            attrs.put(key, new AttrEffective(key, v, src, ctx.paramDefs().get(key)));
        }

        boolean hasOp = op != null;
        // 列型覆盖链（V25 L3）：product_flow 绑定行 > step 覆盖 > 工序默认（col 原语）
        CimProductFlow pf = ctx.productBinding();
        String pfReticle = pf == null ? null : pf.getReticleRef();
        String pfContam = pf == null ? null : pf.getContamLevel();
        String pfContamFb = pf == null ? null : pf.getContamForbidden();
        Integer pfNpw = pf == null ? null : pf.getNpwLimit();
        return new NodeEffective(
                s.getNodeId(), s.getStepCode(), s.getStepName(), s.getStepType(),
                s.getOperationCode(), op == null ? null : op.getStatus(), stale,
                col(null, s.getRecipeRef(), hasOp ? op.getRecipeRef() : null),
                col(null, s.getEquipType(), hasOp ? op.getEquipType() : null),
                intg(null, s.getQtimeMin(), hasOp ? op.getQtimeMin() : null),
                intg(null, s.getQtimeMax(), hasOp ? op.getQtimeMax() : null),
                intg(null, s.getReworkLimit(), hasOp ? op.getReworkLimit() : null),
                col(null, s.getBatchMode(), hasOp ? op.getBatchMode() : null),
                intg(null, s.getBatchSize(), hasOp ? op.getBatchSize() : null),
                bool(null, s.getHoldPoint(), hasOp ? op.getHoldPoint() : null),
                col(null, s.getHoldReason(), hasOp ? op.getHoldReason() : null),
                col(null, s.getInspectPlan(), hasOp ? op.getInspectPlan() : null),
                dec(null, s.getSamplingRate(), hasOp ? op.getSamplingRate() : null),
                col(pfReticle, s.getReticleRef(), hasOp ? op.getReticleRef() : null),
                col(pfContam, s.getContamLevel(), null),
                col(pfContamFb, s.getContamForbidden(), null),
                col(null, s.getCapabilityCode(), hasOp ? op.getCapabilityCode() : null),
                col(null, s.getEdcPlanCode(), hasOp ? op.getEdcPlanCode() : null),
                intg(pfNpw, s.getNpwLimit(), null),
                poolSource, sortedPool, attrs);
    }

    // ---------------- 沿链取值原语（override 非空则用，否则回落默认；都空 = MISSING） ----------------

    /**
     * 列型覆盖链原语（V25）：product_flow 绑定行覆盖（SRC_PRODUCT）&gt; step 覆盖
     * （SRC_STEP）&gt; 工序默认（SRC_OP）；全空 = MISSING。列型约束无 FLOW 级（V22 口径）。
     */
    private static Effective<String> col(String product, String stepOverride, String opDefault) {
        if (product != null && !product.isBlank()) {
            return Effective.of(product, SRC_PRODUCT);
        }
        if (stepOverride != null && !stepOverride.isBlank()) {
            return Effective.of(stepOverride, SRC_STEP);
        }
        if (opDefault != null && !opDefault.isBlank()) {
            return Effective.of(opDefault, SRC_OP);
        }
        return Effective.of(null, SRC_MISSING);
    }

    private static Effective<Integer> intg(Integer product, Integer override, Integer dft) {
        if (product != null) {
            return Effective.of(product, SRC_PRODUCT);
        }
        if (override != null) {
            return Effective.of(override, SRC_STEP);
        }
        if (dft != null) {
            return Effective.of(dft, SRC_OP);
        }
        return Effective.of(null, SRC_MISSING);
    }

    private static Effective<Boolean> bool(Boolean product, Boolean override, Boolean dft) {
        if (product != null) {
            return Effective.of(product, SRC_PRODUCT);
        }
        if (override != null) {
            return Effective.of(override, SRC_STEP);
        }
        if (dft != null) {
            return Effective.of(dft, SRC_OP);
        }
        return Effective.of(null, SRC_MISSING);
    }

    private static Effective<BigDecimal> dec(BigDecimal product, BigDecimal override, BigDecimal dft) {
        if (product != null) {
            return Effective.of(product, SRC_PRODUCT);
        }
        if (override != null) {
            return Effective.of(override, SRC_STEP);
        }
        if (dft != null) {
            return Effective.of(dft, SRC_OP);
        }
        return Effective.of(null, SRC_MISSING);
    }

    private static Map<String, String> kv(List<CimConstraintAttr> rows) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (CimConstraintAttr a : rows) {
            if (a.getAttrValue() != null && !a.getAttrValue().isBlank()) {
                m.put(a.getAttrKey(), a.getAttrValue());
            }
        }
        return m;
    }

    /** PRODUCT 层 KV 合并：细化到站的行 &gt; 整条流程的行（同层先写后写覆盖，站级行后合并覆盖）。 */
    private static Map<String, String> kv(List<CimConstraintAttr> flowLevelRows,
                                          List<CimConstraintAttr> stepLevelRows,
                                          String nodeId) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (CimConstraintAttr a : flowLevelRows) {
            if (a.getAttrValue() != null && !a.getAttrValue().isBlank()) {
                m.put(a.getAttrKey(), a.getAttrValue());
            }
        }
        for (CimConstraintAttr a : stepLevelRows) {
            if (a.getAttrValue() != null && !a.getAttrValue().isBlank()) {
                m.put(a.getAttrKey(), a.getAttrValue());
            }
        }
        return m;
    }

    /** context_type 判定（内部小工具，避免字符串散落）。 */
    private static final class CimConstraintAttrScope {
        static boolean isOperation(CimConstraintAttr a) {
            return CimEquipAssign.SCOPE_OPERATION.equals(a.getContextType());
        }

        static boolean isFlow(CimConstraintAttr a) {
            return CimEquipAssign.SCOPE_FLOW.equals(a.getContextType());
        }

        static boolean isStep(CimConstraintAttr a) {
            return CimEquipAssign.SCOPE_STEP.equals(a.getContextType());
        }
    }
}
