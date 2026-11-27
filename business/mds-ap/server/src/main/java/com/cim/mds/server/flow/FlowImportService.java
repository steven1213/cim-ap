package com.cim.mds.server.flow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cim.mds.server.equipment.CimEquipmentClassRepository;
import com.cim.mds.server.equipment.CimEquipmentRepository;
import com.cim.mds.server.equipment.CimToolGroupRepository;
import com.cim.mds.server.site.CimTechnologyRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程三段式 Excel 导入（P2，P07 导入向导后端；对标 myCIM importExcelWithFod 的
 * 「先删后插 + 表头对照校验」口径）。
 *
 * <p><b>三段</b>（一个工作簿四张 sheet，段 1 是工艺结构、段 2/3 是 ContextChain 覆盖数据）：</p>
 * <ol>
 *   <li>「01_流程」主档 + 「02_节点」节点行——按 flowCode 分组、行序即画布直线顺序，
 *       自动补 START/END 生成直线画布，走 {@link FlowAdminService#saveXml} 全量守门重建；</li>
 *   <li>「03_指派」——scope=OPERATION/FLOW/STEP 三层统一指派，按上下文锚点<b>先删后插</b>
 *       （sheet 即该锚点的候选池全量快照，与 UI PUT 语义一致）；</li>
 *   <li>「04_参数」——scope 三层 KV，按上下文锚点先删后插（快照语义同上）。</li>
 * </ol>
 *
 * <p><b>两阶段</b>：{@link #preview(byte[])} 解析 + 全量校验返回错误清单（不落库）；
 * {@link #apply(byte[])} 校验通过后事务落库（原子：任一落库异常整体回滚）。
 * dryRun 用 preview；确认提交用 apply。</p>
 *
 * <p><b>范围守门</b>：已存在流程仅 UNFROZEN 且未锁定可被导入覆盖（节点/指派/参数段），
 * 他人锁定或已冻结 → 逐行报错（不中止其余流程）。指派/参数先删后插的删除范围仅限
 * sheet 中出现的锚点，未出现的既有数据不动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowImportService {

    private final FlowAdminService flowAdminService;
    private final CimFlowRepository flowRepository;
    private final CimOperationRepository operationRepository;
    private final CimTechnologyRepository technologyRepository;
    private final CimEquipmentClassRepository equipmentClassRepository;
    private final CimToolGroupRepository toolGroupRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final CimEquipAssignRepository assignRepository;
    private final CimConstraintAttrRepository attrRepository;
    private final CimParamDefRepository paramDefRepository;
    private final FlowEventService flowEventService;
    private final CimFlowStepRepository stepRepository;

    // ------------------------------------------------------------------ sheet/列契约

    public static final String SHEET_FLOW = "01_流程";
    public static final String SHEET_STEP = "02_节点";
    public static final String SHEET_ASSIGN = "03_指派";
    public static final String SHEET_ATTR = "04_参数";

    private static final String[] FLOW_HEADERS = {
            "flowCode*", "flowName*", "techCode", "flowKind", "stageCode",
            "reworkOfFamily", "reworkCountLimit", "remark"};
    private static final String[] STEP_HEADERS = {
            "flowCode*", "nodeId*", "stepCode*", "stepName", "stepType*",
            "operationCode", "recipeRef", "equipType", "qtimeMin", "qtimeMax",
            "reworkLimit", "npwLimit", "batchMode", "batchSize", "holdPoint",
            "holdReason", "inspectPlan", "samplingRate", "capabilityCode",
            "edcPlanCode", "reticleRef", "contamLevel", "contamForbidden"};
    private static final String[] ASSIGN_HEADERS = {
            "scope*", "operationCode", "flowCode", "nodeId", "assignType*",
            "assignRef*", "priority", "recipeRule", "recipeRef", "recipeMatch",
            "chamberRule", "enabled"};
    private static final String[] ATTR_HEADERS = {
            "scope*", "operationCode", "flowCode", "nodeId", "attrKey*", "attrValue"};

    private static final Set<String> BUSINESS_TYPES = Set.of(
            CimFlowStep.TYPE_PROCESS, CimFlowStep.TYPE_INSPECT,
            CimFlowStep.TYPE_REWORK, CimFlowStep.TYPE_DECISION);
    private static final Set<String> SCOPES = Set.of(
            CimEquipAssign.SCOPE_OPERATION, CimEquipAssign.SCOPE_FLOW, CimEquipAssign.SCOPE_STEP);
    private static final Set<String> ASSIGN_TYPES = Set.of(
            CimEquipAssign.TYPE_GROUP, CimEquipAssign.TYPE_EQUIP, CimEquipAssign.TYPE_CLASS);
    private static final Set<String> RECIPE_RULES = Set.of(
            CimEquipAssign.RULE_FIXED, CimEquipAssign.RULE_GENERATE, CimEquipAssign.RULE_EXACT_MATCH);

    // ------------------------------------------------------------------ DTO

    /** 单条错误（sheet + 1-based 行号 + 原因；导入报告展示用描述串）。 */
    public record ImportError(String sheet, int row, String message) {
    }

    /** 预览（dryRun）：各段行数 + 全量错误清单（errors 非空 = 不可提交）。 */
    public record ImportPreview(int flowRows, int stepRows, int assignRows, int attrRows,
                                List<ImportError> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    /** 落库结果（apply）：各段导入计数。 */
    public record ImportResult(int flowsCreated, int flowsUpdated, int stepsImported,
                               int assignsImported, int attrsImported) {
    }

    // 段间中间态（包内可见，preview/apply 共用解析口径）
    record FlowRow(String flowCode, String flowName, String techCode, String flowKind,
                   String stageCode, String reworkOfFamily, Integer reworkCountLimit, String remark) {
    }

    record StepRow(String flowCode, String nodeId, String stepCode, String stepName, String stepType,
                   String operationCode, String recipeRef, String equipType, String qtimeMin,
                   String qtimeMax, String reworkLimit, String npwLimit, String batchMode,
                   String batchSize, Boolean holdPoint, String holdReason, String inspectPlan,
                   String samplingRate, String capabilityCode, String edcPlanCode,
                   String reticleRef, String contamLevel, String contamForbidden) {
    }

    record AssignRow(String scope, String operationCode, String flowCode, String nodeId,
                     String assignType, String assignRef, Integer priority, String recipeRule,
                     String recipeRef, String recipeMatch, String chamberRule, Boolean enabled) {
    }

    record AttrRow(String scope, String operationCode, String flowCode, String nodeId,
                   String attrKey, String attrValue) {
    }

    record Parsed(List<FlowRow> flows, List<StepRow> steps, List<AssignRow> assigns,
                  List<AttrRow> attrs) {
    }

    // ------------------------------------------------------------------ 模板生成

    /** 生成三段式导入模板（4 sheet：表头 + 1 条示例行；示例行以 # 开头即注释行可删除）。 */
    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            exampleSheet(wb, SHEET_FLOW, FLOW_HEADERS,
                    "FLEX001|示例流程|T6|MAIN|ST-LITHO||12|由模板生成后按行填写");
            exampleSheet(wb, SHEET_STEP, STEP_HEADERS,
                    "FLEX001|n1|STP010|氧化|PROCESS|OP-OX-01||EQ-OX|120|240||||SERIAL|12|false|||0||||");
            exampleSheet(wb, SHEET_ASSIGN, ASSIGN_HEADERS,
                    "FLOW|FLEX001|||GROUP|TG-DEMO|100|GENERATE||||true");
            exampleSheet(wb, SHEET_ATTR, ATTR_HEADERS,
                    "STEP|FLEX001|n1|max_reticle|4");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException(BizCode.PARAM_INVALID, "模板生成失败: " + e.getMessage());
        }
    }

    private void exampleSheet(XSSFWorkbook wb, String name, String[] headers, String example) {
        Sheet sh = wb.createSheet(name);
        Row head = sh.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            head.createCell(i).setCellValue(headers[i]);
        }
        Row ex = sh.createRow(1);
        String[] cols = example.split("\\|", -1);
        for (int i = 0; i < headers.length; i++) {
            ex.createCell(i).setCellValue(i < cols.length ? cols[i] : "");
        }
        for (int i = 0; i < headers.length; i++) {
            sh.setColumnWidth(i, 18 * 256);
        }
    }

    // ------------------------------------------------------------------ 解析 + 校验

    /** dryRun：解析 + 全量校验，不落库。 */
    @Transactional(readOnly = true)
    public ImportPreview preview(byte[] data) {
        Parsed p = parseAll(data);
        List<ImportError> errors = validateAll(p);
        return new ImportPreview(p.flows().size(), p.steps().size(),
                p.assigns().size(), p.attrs().size(), errors);
    }

    /** 提交导入：校验通过后事务落库（流程 upsert → 节点直线画布 → 指派/参数先删后插）。 */
    @Transactional
    public ImportResult apply(byte[] data) {
        Parsed p = parseAll(data);
        List<ImportError> errors = validateAll(p);
        if (!errors.isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.import.hasErrors", errors.size());
        }
        int flowsCreated = 0;
        int flowsUpdated = 0;
        Map<String, String> flowIdByCode = new HashMap<>();
        for (FlowRow f : p.flows()) {
            CimFlow existing = flowRepository.findByFlowCodeAndDeletedFalse(f.flowCode()).orElse(null);
            if (existing == null) {
                FlowAdminService.FlowView created = flowAdminService.create(new FlowAdminService.FlowReq(
                        f.flowCode(), f.flowName(), f.techCode(), f.remark(), null,
                        f.flowKind(), f.stageCode(), f.reworkOfFamily(), f.reworkCountLimit()));
                flowIdByCode.put(f.flowCode(), created.id());
                flowsCreated++;
            } else {
                requireEditable(existing, f.flowCode());
                existing.setFlowName(f.flowName());
                existing.setTechCode(f.techCode());
                existing.setRemark(f.remark());
                if (f.flowKind() != null) {
                    existing.setFlowKind(f.flowKind());
                }
                existing.setStageCode(f.stageCode());
                existing.setReworkOfFamily(f.reworkOfFamily());
                existing.setReworkCountLimit(f.reworkCountLimit());
                flowRepository.save(existing);
                flowEventService.record(existing.getId(), existing.getFlowCode(), FlowEventService.T_FLOW,
                        "UPDATE", FlowEventService.summary("via", "import", "flowName", existing.getFlowName()));
                flowIdByCode.put(f.flowCode(), existing.getId());
                flowsUpdated++;
            }
        }
        // 节点段：flowCode 分组（保序）→ 直线画布 → saveXml 全量守门重建
        int stepsImported = 0;
        Map<String, List<StepRow>> stepsByFlow = new LinkedHashMap<>();
        for (StepRow s : p.steps()) {
            stepsByFlow.computeIfAbsent(s.flowCode(), k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<String, List<StepRow>> e : stepsByFlow.entrySet()) {
            String flowId = requireFlowId(e.getKey(), flowIdByCode);
            flowAdminService.saveXml(flowId,
                    new FlowAdminService.CanvasReq(buildLinearXml(e.getValue())));
            stepsImported += e.getValue().size();
        }
        // 指派段：按上下文锚点先删后插（sheet 即该锚点全量快照）
        int assignsImported = 0;
        Set<String> assignAnchors = new HashSet<>();
        for (AssignRow a : p.assigns()) {
            assignAnchors.add(anchor(a.scope(), a.operationCode(), a.flowCode(), a.nodeId()));
        }
        for (String anchor : assignAnchors) {
            deleteAssignsByAnchor(anchor, flowIdByCode);
        }
        for (AssignRow a : p.assigns()) {
            CimEquipAssign entity = new CimEquipAssign();
            fillAssign(entity, a, flowIdByCode);
            assignRepository.save(entity);
            assignsImported++;
        }
        // 参数段：按上下文锚点先删后插
        int attrsImported = 0;
        Set<String> attrAnchors = new HashSet<>();
        for (AttrRow a : p.attrs()) {
            attrAnchors.add(anchor(a.scope(), a.operationCode(), a.flowCode(), a.nodeId()));
        }
        for (String anchor : attrAnchors) {
            deleteAttrsByAnchor(anchor, flowIdByCode);
        }
        for (AttrRow a : p.attrs()) {
            CimConstraintAttr entity = new CimConstraintAttr();
            fillAttr(entity, a, flowIdByCode);
            attrRepository.save(entity);
            attrsImported++;
        }
        // 事件历史（V24）：主档行补 IMPORT 标记（节点/指派/参数段已由 saveXml/锚点替换各自落事件）
        for (Map.Entry<String, String> en : flowIdByCode.entrySet()) {
            CimFlow row = flowRepository.findById(en.getValue()).orElse(null);
            if (row == null) {
                continue;
            }
            flowEventService.record(en.getValue(), row.getFlowCode(), FlowEventService.T_FLOW,
                    "IMPORT", FlowEventService.summary("steps", stepsImported,
                            "assigns", assignsImported, "attrs", attrsImported));
        }
        return new ImportResult(flowsCreated, flowsUpdated, stepsImported, assignsImported, attrsImported);
    }

    // ------------------------------------------------------------------ 解析

    private Parsed parseAll(byte[] data) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            return new Parsed(
                    parseFlow(sheetOf(wb, SHEET_FLOW)),
                    parseStep(sheetOf(wb, SHEET_STEP)),
                    parseAssign(sheetOf(wb, SHEET_ASSIGN)),
                    parseAttr(sheetOf(wb, SHEET_ATTR)));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "Excel 解析失败: " + e.getMessage());
        }
    }

    private Sheet sheetOf(Workbook wb, String name) {
        Sheet sh = wb.getSheet(name);
        if (sh == null) {
            throw new BizException(BizCode.PARAM_INVALID, "缺少工作表: " + name);
        }
        return sh;
    }

    private List<FlowRow> parseFlow(Sheet sh) {
        List<FlowRow> out = new ArrayList<>();
        for (int r = 1; r <= sh.getLastRowNum(); r++) {
            Row row = sh.getRow(r);
            if (isEmptyRow(row, FLOW_HEADERS.length)) {
                continue;
            }
            out.add(new FlowRow(
                    str(row, 0), str(row, 1), str(row, 2), str(row, 3), str(row, 4),
                    str(row, 5), intOpt(row, 6, SHEET_FLOW, r + 1, "reworkCountLimit"),
                    str(row, 7)));
        }
        return out;
    }

    private List<StepRow> parseStep(Sheet sh) {
        List<StepRow> out = new ArrayList<>();
        for (int r = 1; r <= sh.getLastRowNum(); r++) {
            Row row = sh.getRow(r);
            if (isEmptyRow(row, STEP_HEADERS.length)) {
                continue;
            }
            out.add(new StepRow(
                    str(row, 0), str(row, 1), str(row, 2), str(row, 3), str(row, 4),
                    str(row, 5), str(row, 6), str(row, 7),
                    intStrOpt(row, 8), intStrOpt(row, 9), intStrOpt(row, 10), intStrOpt(row, 11),
                    str(row, 12), str(row, 13), boolOpt(row, 14), str(row, 15),
                    str(row, 16), str(row, 17), str(row, 18), str(row, 19),
                    str(row, 20), str(row, 21), str(row, 22)));
        }
        return out;
    }

    private List<AssignRow> parseAssign(Sheet sh) {
        List<AssignRow> out = new ArrayList<>();
        for (int r = 1; r <= sh.getLastRowNum(); r++) {
            Row row = sh.getRow(r);
            if (isEmptyRow(row, ASSIGN_HEADERS.length)) {
                continue;
            }
            out.add(new AssignRow(
                    upper(str(row, 0)), str(row, 1), str(row, 2), str(row, 3),
                    upper(str(row, 4)), str(row, 5), intOpt(row, 6, SHEET_ASSIGN, r + 1, "priority"),
                    upper(str(row, 7)), str(row, 8), str(row, 9), str(row, 10),
                    boolOpt(row, 11)));
        }
        return out;
    }

    private List<AttrRow> parseAttr(Sheet sh) {
        List<AttrRow> out = new ArrayList<>();
        for (int r = 1; r <= sh.getLastRowNum(); r++) {
            Row row = sh.getRow(r);
            if (isEmptyRow(row, ATTR_HEADERS.length)) {
                continue;
            }
            out.add(new AttrRow(upper(str(row, 0)), str(row, 1), str(row, 2), str(row, 3),
                    str(row, 4), str(row, 5)));
        }
        return out;
    }

    // ------------------------------------------------------------------ 校验（全量清单，不抛首个即停）

    private List<ImportError> validateAll(Parsed p) {
        List<ImportError> out = new ArrayList<>();
        // 流程段
        Set<String> flowCodes = new HashSet<>();
        int rn = 1;
        for (FlowRow f : p.flows()) {
            rn++;
            if (blank(f.flowCode()) || blank(f.flowName())) {
                out.add(new ImportError(SHEET_FLOW, rn, "flowCode/flowName 必填"));
                continue;
            }
            if (!flowCodes.add(f.flowCode())) {
                out.add(new ImportError(SHEET_FLOW, rn, "flowCode 重复: " + f.flowCode()));
            }
            if (f.techCode() != null
                    && technologyRepository.findByTechCodeAndDeletedFalse(f.techCode()).isEmpty()) {
                out.add(new ImportError(SHEET_FLOW, rn, "techCode 不存在: " + f.techCode()));
            }
            if (f.flowKind() != null && !f.flowKind().isBlank()
                    && !CimFlow.KIND_MAIN.equals(f.flowKind()) && !CimFlow.KIND_SUB.equals(f.flowKind())) {
                out.add(new ImportError(SHEET_FLOW, rn,
                        "flowKind 非法: " + f.flowKind() + "（MAIN/SUB）"));
            }
            if (f.reworkCountLimit() != null && f.reworkCountLimit() <= 0) {
                out.add(new ImportError(SHEET_FLOW, rn, "reworkCountLimit 须为正整数"));
            }
            // 已存在流程的状态守门提前到 dryRun（错误清单一次暴露，不等到落库才炸）
            CimFlow existing = flowRepository.findByFlowCodeAndDeletedFalse(f.flowCode()).orElse(null);
            if (existing != null) {
                String err = editableError(existing);
                if (err != null) {
                    out.add(new ImportError(SHEET_FLOW, rn, err));
                }
            }
        }
        Set<String> knownFlows = new HashSet<>(flowCodes);
        // 节点段
        Map<String, Set<String>> nodeIdsByFlow = new HashMap<>();
        Map<String, Set<String>> stepCodesByFlow = new HashMap<>();
        Set<String> checkedEditable = new HashSet<>();
        rn = 1;
        for (StepRow s : p.steps()) {
            rn++;
            if (blank(s.flowCode()) || blank(s.nodeId()) || blank(s.stepCode()) || blank(s.stepType())) {
                out.add(new ImportError(SHEET_STEP, rn, "flowCode/nodeId/stepCode/stepType 必填"));
                continue;
            }
            if (!BUSINESS_TYPES.contains(s.stepType())) {
                out.add(new ImportError(SHEET_STEP, rn,
                        "stepType 须为业务类型（PROCESS/INSPECT/REWORK/DECISION；START/END 自动补）: " + s.stepType()));
                continue;
            }
            if (!nodeIdsByFlow.computeIfAbsent(s.flowCode(), k -> new HashSet<>()).add(s.nodeId())) {
                out.add(new ImportError(SHEET_STEP, rn,
                        "同流程 nodeId 重复: " + s.flowCode() + "/" + s.nodeId()));
            }
            if (!stepCodesByFlow.computeIfAbsent(s.flowCode(), k -> new HashSet<>()).add(s.stepCode())) {
                out.add(new ImportError(SHEET_STEP, rn,
                        "同流程 stepCode 重复: " + s.flowCode() + "/" + s.stepCode()));
            }
            if (s.operationCode() != null) {
                CimOperation op = operationRepository
                        .findByOpCodeAndDeletedFalse(s.operationCode()).orElse(null);
                if (op == null) {
                    out.add(new ImportError(SHEET_STEP, rn, "operationCode 不存在: " + s.operationCode()));
                } else if (!CimOperation.STATUS_ACTIVE.equals(op.getStatus())) {
                    out.add(new ImportError(SHEET_STEP, rn,
                            "operationCode 非 ACTIVE: " + s.operationCode() + "（" + op.getStatus() + "）"));
                } else if (CimFlowStep.TYPE_INSPECT.equals(s.stepType())
                        && !CimOperation.TYPE_INSPECT.equals(op.getOpType())) {
                    out.add(new ImportError(SHEET_STEP, rn,
                            "INSPECT 节点须引用检检类工序: " + s.operationCode()));
                }
            }
            if (s.equipType() != null
                    && equipmentClassRepository.findByClassCodeAndDeletedFalse(s.equipType()).isEmpty()) {
                out.add(new ImportError(SHEET_STEP, rn, "equipType 不存在: " + s.equipType()));
            }
            knownFlows.add(s.flowCode());
            // 节点段触及的已存在流程（未出现在流程 sheet 的）同样提前守门
            if (!flowCodes.contains(s.flowCode()) && !checkedEditable.contains(s.flowCode())) {
                CimFlow existing = flowRepository.findByFlowCodeAndDeletedFalse(s.flowCode()).orElse(null);
                if (existing != null) {
                    String err = editableError(existing);
                    if (err != null) {
                        out.add(new ImportError(SHEET_STEP, rn, err));
                    }
                }
            }
            checkedEditable.add(s.flowCode());
        }
        // 指派段
        rn = 1;
        for (AssignRow a : p.assigns()) {
            rn++;
            if (!SCOPES.contains(a.scope())) {
                out.add(new ImportError(SHEET_ASSIGN, rn, "scope 非法: " + a.scope()));
                continue;
            }
            if (!ASSIGN_TYPES.contains(a.assignType())) {
                out.add(new ImportError(SHEET_ASSIGN, rn, "assignType 非法: " + a.assignType()));
                continue;
            }
            String anchorErr = anchorError(a.scope(), a.operationCode(), a.flowCode(), a.nodeId(),
                    knownFlows, nodeIdsByFlow);
            if (anchorErr != null) {
                out.add(new ImportError(SHEET_ASSIGN, rn, anchorErr));
                continue;
            }
            if (!checkRef(a.assignType(), a.assignRef())) {
                out.add(new ImportError(SHEET_ASSIGN, rn,
                        a.assignType() + " 引用不存在或已停用: " + a.assignRef()));
            }
            String rule = a.recipeRule() == null ? CimEquipAssign.RULE_FIXED : a.recipeRule();
            if (!RECIPE_RULES.contains(rule)) {
                out.add(new ImportError(SHEET_ASSIGN, rn, "recipeRule 非法: " + rule));
            } else if (CimEquipAssign.RULE_FIXED.equals(rule) && blank(a.recipeRef())) {
                out.add(new ImportError(SHEET_ASSIGN, rn, "recipeRule=FIXED 时 recipeRef 必填"));
            }
        }
        // 参数段
        rn = 1;
        for (AttrRow a : p.attrs()) {
            rn++;
            if (!SCOPES.contains(a.scope())) {
                out.add(new ImportError(SHEET_ATTR, rn, "scope 非法: " + a.scope()));
                continue;
            }
            String anchorErr = anchorError(a.scope(), a.operationCode(), a.flowCode(), a.nodeId(),
                    knownFlows, nodeIdsByFlow);
            if (anchorErr != null) {
                out.add(new ImportError(SHEET_ATTR, rn, anchorErr));
                continue;
            }
            if (blank(a.attrKey())) {
                out.add(new ImportError(SHEET_ATTR, rn, "attrKey 必填"));
                continue;
            }
            CimParamDef def = paramDefRepository.findByAttrKeyAndDeletedFalse(a.attrKey())
                    .orElse(null);
            if (def == null || !Boolean.TRUE.equals(def.getEnabled())) {
                out.add(new ImportError(SHEET_ATTR, rn, "attrKey 未注册或已停用: " + a.attrKey()));
            } else if (def.getScopes() == null || !def.getScopes().contains(a.scope())) {
                out.add(new ImportError(SHEET_ATTR, rn,
                        "参数 " + a.attrKey() + " 的 scopes 不含 " + a.scope()));
            }
        }
        return out;
    }

    /** 上下文锚点合法性：scope 与键列匹配 + flowCode 可解析（本文件或库内）+ STEP 的 nodeId 已存在。 */
    private String anchorError(String scope, String operationCode, String flowCode, String nodeId,
                               Set<String> knownFlows, Map<String, Set<String>> nodeIdsByFlow) {
        switch (scope) {
            case CimEquipAssign.SCOPE_OPERATION -> {
                if (blank(operationCode)) {
                    return "scope=OPERATION 时 operationCode 必填";
                }
                return null;
            }
            case CimEquipAssign.SCOPE_FLOW, CimEquipAssign.SCOPE_STEP -> {
                if (blank(flowCode)) {
                    return "scope=" + scope + " 时 flowCode 必填";
                }
                String flowId;
                if (knownFlows.contains(flowCode)) {
                    flowId = flowRepository.findByFlowCodeAndDeletedFalse(flowCode)
                            .map(CimFlow::getId).orElse(""); // 本文件内新建/更新的流程（apply 时先落主档）
                } else {
                    flowId = resolveFlowId(flowCode);
                }
                if (flowId == null) {
                    return "flowCode 不存在于本文件与流程库: " + flowCode;
                }
                if (CimEquipAssign.SCOPE_STEP.equals(scope)) {
                    if (blank(nodeId)) {
                        return "scope=STEP 时 nodeId 必填";
                    }
                    Set<String> ids = nodeIdsByFlow.get(flowCode);
                    if ((ids == null || !ids.contains(nodeId)) && !stepNodeIdExists(flowId, nodeId)) {
                        return "该流程不存在节点 nodeId: " + flowCode + "/" + nodeId;
                    }
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** 流程编码解析：库内查询（可空 = 不存在；dryRun/apply 共用同一口径）。 */
    private String resolveFlowId(String flowCode) {
        return flowRepository.findByFlowCodeAndDeletedFalse(flowCode)
                .map(CimFlow::getId).orElse(null);
    }

    /** 库内节点存在性（STEP 锚点引用不在本文件节点 sheet 的既有节点时）。 */
    private boolean stepNodeIdExists(String flowId, String nodeId) {
        return stepRepository.findByFlowIdAndDeletedFalseOrderByStepNoAsc(flowId)
                .stream().anyMatch(s -> nodeId.equals(s.getNodeId()));
    }

    private boolean checkRef(String type, String ref) {
        if (blank(ref)) {
            return false;
        }
        return switch (type) {
            case CimEquipAssign.TYPE_GROUP -> toolGroupRepository
                    .findByGroupCodeAndDeletedFalse(ref)
                    .map(g -> Boolean.TRUE.equals(g.getEnabled())).orElse(false);
            case CimEquipAssign.TYPE_EQUIP -> equipmentRepository
                    .findByEquipmentCodeAndDeletedFalse(ref)
                    .map(e -> Boolean.TRUE.equals(e.getEnabled())).orElse(false);
            case CimEquipAssign.TYPE_CLASS -> equipmentClassRepository
                    .findByClassCodeAndDeletedFalse(ref).isPresent();
            default -> false;
        };
    }

    // ------------------------------------------------------------------ 落库辅助

    /** 已存在流程可被导入覆盖的前提：UNFROZEN 且未被他人锁定（返回 null = 可覆盖）。 */
    private String editableError(CimFlow flow) {
        if (!CimFlow.STATUS_UNFROZEN.equals(flow.getStatus())) {
            return "流程 " + flow.getFlowCode() + " 当前状态 " + flow.getStatus()
                    + " 不可导入覆盖（仅 UNFROZEN）";
        }
        if (Boolean.TRUE.equals(flow.getIsOccupied())
                && flow.getOccupiedBy() != null
                && !flow.getOccupiedBy().equals(com.cim.mds.server.site.EditLockSupport.currentUser())) {
            return "流程 " + flow.getFlowCode() + " 正被 " + flow.getOccupiedBy() + " 锁定编辑";
        }
        return null;
    }

    /** 已存在流程可被导入覆盖的前提：UNFROZEN 且未被他人锁定。 */
    private void requireEditable(CimFlow flow, String code) {
        if (!CimFlow.STATUS_UNFROZEN.equals(flow.getStatus())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.importNotEditable", code, flow.getStatus());
        }
        if (Boolean.TRUE.equals(flow.getIsOccupied())
                && flow.getOccupiedBy() != null
                && !flow.getOccupiedBy().equals(com.cim.mds.server.site.EditLockSupport.currentUser())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.flow.importLockedByOther", code, flow.getOccupiedBy());
        }
    }

    private String requireFlowId(String flowCode, Map<String, String> flowIdByCode) {
        String id = flowIdByCode.get(flowCode);
        if (id != null) {
            return id;
        }
        CimFlow f = flowRepository.findByFlowCodeAndDeletedFalse(flowCode)
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.flow.importFlowMissing", flowCode));
        requireEditable(f, flowCode);
        return f.getId();
    }

    private String anchor(String scope, String opCode, String flowCode, String nodeId) {
        return scope + "|" + nz(opCode) + "|" + nz(flowCode) + "|" + nz(nodeId);
    }

    private void deleteAssignsByAnchor(String anchor, Map<String, String> flowIdByCode) {
        String[] parts = anchor.split("\\|", -1);
        String scope = parts[0];
        switch (scope) {
            case CimEquipAssign.SCOPE_OPERATION -> assignRepository
                    .findByScopeTypeAndOperationCodeAndDeletedFalse(scope, parts[1])
                    .forEach(assignRepository::delete);
            case CimEquipAssign.SCOPE_FLOW -> assignRepository
                    .findByScopeTypeAndFlowIdAndDeletedFalse(scope, requireFlowIdIn(parts[2], flowIdByCode))
                    .forEach(assignRepository::delete);
            case CimEquipAssign.SCOPE_STEP -> assignRepository
                    .findByScopeTypeAndFlowIdAndStepIdAndDeletedFalse(
                            scope, requireFlowIdIn(parts[2], flowIdByCode), parts[3])
                    .forEach(assignRepository::delete);
            default -> {
            }
        }
    }

    private void deleteAttrsByAnchor(String anchor, Map<String, String> flowIdByCode) {
        String[] parts = anchor.split("\\|", -1);
        String scope = parts[0];
        switch (scope) {
            case CimEquipAssign.SCOPE_OPERATION -> attrRepository
                    .findByContextTypeAndOperationCodeAndDeletedFalse(scope, parts[1])
                    .forEach(attrRepository::delete);
            case CimEquipAssign.SCOPE_FLOW -> attrRepository
                    .findByContextTypeAndFlowIdAndDeletedFalse(scope, requireFlowIdIn(parts[2], flowIdByCode))
                    .forEach(attrRepository::delete);
            case CimEquipAssign.SCOPE_STEP -> attrRepository
                    .findByContextTypeAndFlowIdAndStepIdAndDeletedFalse(
                            scope, requireFlowIdIn(parts[2], flowIdByCode), parts[3])
                    .forEach(attrRepository::delete);
            default -> {
            }
        }
    }

    /** 锚点 flowId 解析：优先本文件落库缓存，否则查库（指派/参数段可只引用既有流程）。 */
    private String requireFlowIdIn(String flowCode, Map<String, String> flowIdByCode) {
        String id = flowIdByCode.get(flowCode);
        if (id != null && !id.isBlank()) {
            return id;
        }
        id = flowRepository.findByFlowCodeAndDeletedFalse(flowCode)
                .map(CimFlow::getId).orElse(null);
        if (id == null) {
            throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.flow.importFlowMissing", flowCode);
        }
        flowIdByCode.put(flowCode, id);
        return id;
    }

    private void fillAssign(CimEquipAssign e, AssignRow a, Map<String, String> flowIdByCode) {
        e.setScopeType(a.scope());
        e.setOperationCode(CimEquipAssign.SCOPE_OPERATION.equals(a.scope())
                ? a.operationCode() : nz(a.operationCode()));
        e.setFlowId(a.flowCode() == null ? "" : flowIdByCode.computeIfAbsent(a.flowCode(),
                code -> {
                    CimFlow f = flowRepository.findByFlowCodeAndDeletedFalse(code)
                            .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                                    "mds.biz.flow.importFlowMissing", code));
                    requireEditable(f, code);
                    return f.getId();
                }));
        e.setStepId(a.nodeId() == null ? "" : a.nodeId());
        e.setAssignType(a.assignType());
        e.setAssignRef(a.assignRef());
        e.setPriority(a.priority() == null ? 100 : a.priority());
        e.setRecipeRule(a.recipeRule() == null ? CimEquipAssign.RULE_FIXED : a.recipeRule());
        e.setRecipeRef(a.recipeRef());
        e.setRecipeMatch(a.recipeMatch());
        e.setChamberRule(a.chamberRule());
        e.setEnabled(a.enabled() == null || a.enabled());
    }

    private void fillAttr(CimConstraintAttr e, AttrRow a, Map<String, String> flowIdByCode) {
        e.setContextType(a.scope());
        e.setOperationCode(CimEquipAssign.SCOPE_OPERATION.equals(a.scope())
                ? a.operationCode() : nz(a.operationCode()));
        e.setFlowId(a.flowCode() == null ? "" : flowIdByCode.computeIfAbsent(a.flowCode(),
                code -> {
                    CimFlow f = flowRepository.findByFlowCodeAndDeletedFalse(code)
                            .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                                    "mds.biz.flow.importFlowMissing", code));
                    requireEditable(f, code);
                    return f.getId();
                }));
        e.setStepId(a.nodeId() == null ? "" : a.nodeId());
        e.setAttrKey(a.attrKey());
        e.setAttrValue(a.attrValue());
    }

    /** 节点行 → 直线画布 XML（START/END 自动补；属性空值按契约写空串或省略）。 */
    private String buildLinearXml(List<StepRow> steps) {
        StringBuilder sb = new StringBuilder(512 + steps.size() * 320);
        sb.append("<canvas version=\"1\"><nodes>");
        sb.append("<node id=\"imp-start\" stepCode=\"START\" stepType=\"START\" x=\"40\" y=\"200\"/>");
        int x = 180;
        for (int i = 0; i < steps.size(); i++) {
            StepRow s = steps.get(i);
            sb.append("<node id=\"").append(xesc(s.nodeId())).append('"');
            attr(sb, "stepCode", s.stepCode());
            attr(sb, "stepName", s.stepName());
            sb.append(" stepType=\"").append(s.stepType()).append('"');
            attr(sb, "operationCode", s.operationCode());
            attr(sb, "recipeRef", s.recipeRef());
            attr(sb, "equipType", s.equipType());
            attr(sb, "qtimeMin", s.qtimeMin());
            attr(sb, "qtimeMax", s.qtimeMax());
            attr(sb, "reworkLimit", s.reworkLimit());
            attr(sb, "npwLimit", s.npwLimit());
            attr(sb, "batchMode", s.batchMode());
            attr(sb, "batchSize", s.batchSize());
            if (s.holdPoint() != null) {
                sb.append(" holdPoint=\"").append(s.holdPoint()).append('"');
            }
            attr(sb, "holdReason", s.holdReason());
            attr(sb, "inspectPlan", s.inspectPlan());
            attr(sb, "samplingRate", s.samplingRate());
            attr(sb, "capabilityCode", s.capabilityCode());
            attr(sb, "edcPlanCode", s.edcPlanCode());
            attr(sb, "reticleRef", s.reticleRef());
            attr(sb, "contamLevel", s.contamLevel());
            attr(sb, "contamForbidden", s.contamForbidden());
            sb.append(" x=\"").append(x).append("\" y=\"200\"/>");
            x += 140;
        }
        sb.append("<node id=\"imp-end\" stepCode=\"END\" stepType=\"END\" x=\"").append(x).append("\" y=\"200\"/>");
        sb.append("</nodes><edges>");
        sb.append("<edge id=\"e0\" from=\"imp-start\" to=\"").append(xesc(steps.get(0).nodeId()))
                .append("\" edgeType=\"MAIN\"/>");
        for (int i = 0; i < steps.size() - 1; i++) {
            sb.append("<edge id=\"e").append(i + 1).append("\" from=\"").append(xesc(steps.get(i).nodeId()))
                    .append("\" to=\"").append(xesc(steps.get(i + 1).nodeId())).append("\" edgeType=\"MAIN\"/>");
        }
        sb.append("<edge id=\"e").append(steps.size()).append("\" from=\"")
                .append(xesc(steps.get(steps.size() - 1).nodeId()))
                .append("\" to=\"imp-end\" edgeType=\"MAIN\"/>");
        sb.append("</edges></canvas>");
        return sb.toString();
    }

    private void attr(StringBuilder sb, String name, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(' ').append(name).append("=\"").append(xesc(value)).append('"');
        }
    }

    private static String xesc(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------ POI 单元格读取

    private boolean isEmptyRow(Row row, int cols) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < cols; i++) {
            Cell c = row.getCell(i);
            if (c != null && c.getCellType() != CellType.BLANK
                    && !(c.getCellType() == CellType.STRING && c.getStringCellValue().isBlank())) {
                return false;
            }
        }
        return true;
    }

    /** 文本列（数字单元格转无小数字符串，避免 "12.0" 污染编码列）。 */
    private String str(Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c == null) {
            return null;
        }
        if (c.getCellType() == CellType.NUMERIC) {
            double d = c.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        String v = c.toString();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private Integer intOpt(Row row, int idx, String sheet, int rowNo, String col) {
        String v = str(row, idx);
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            throw new BizException(BizCode.PARAM_INVALID,
                    sheet + " 第 " + rowNo + " 行 " + col + " 不是整数: " + v);
        }
    }

    /** 节点数值列保持字符串（交给 saveXml 统一 parseNullableInt 守门与错误定位）。 */
    private String intStrOpt(Row row, int idx) {
        return str(row, idx);
    }

    private Boolean boolOpt(Row row, int idx) {
        String v = str(row, idx);
        if (v == null) {
            return null;
        }
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "是".equals(v) || "Y".equalsIgnoreCase(v);
    }

    private String upper(String v) {
        return v == null ? null : v.trim().toUpperCase();
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    private boolean blank(String v) {
        return v == null || v.isBlank();
    }
}
