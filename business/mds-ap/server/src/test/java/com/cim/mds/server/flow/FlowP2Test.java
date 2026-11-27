package com.cim.mds.server.flow;

import com.cim.spring.support.web.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流程域 P2 集成测试（V23）：generateDesign 直线流程生成（工序列 → START/N×PROCESS/END
 * 直线画布 + 工序默认带出）、三段式 Excel 导入（dryRun 校验清单 / 提交落库：流程 upsert、
 * 节点直线画布、指派与参数先删后插）、导入范围守门（FROZEN 流程 dryRun 即报错）。
 *
 * <p>认证关闭直通；独立 H2 库名（各测类隔离）；服务直调（不走 multipart 通道）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-p2-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowP2Test {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    FlowImportService importService;

    private static String opA;
    private static String opB;
    private static String flowId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- helpers

    private String postJson(String url, String body, int expectStatus) throws Exception {
        MvcResult r = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "{}" : body))
                .andReturn();
        int status = r.getResponse().getStatus();
        String out = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (status != expectStatus) {
            throw new AssertionError("POST " + url + " expected " + expectStatus
                    + " but was " + status + ": " + out);
        }
        return out;
    }

    private String getJson(String url) throws Exception {
        MvcResult r = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 建工序并激活（generateDesign 只吃 ACTIVE）。 */
    private String createActiveOp(String code, String name) throws Exception {
        String body = postJson("/api/v1/mds/operations", om.writeValueAsString(Map.of(
                "opCode", code, "opName", name, "opType", "PROCESS",
                "equipType", "ET-ETCH", "recipeRef", "R-" + code)), 200);
        String id = om.readTree(body).path("data").path("id").asText();
        postJson("/api/v1/mds/operations/" + id + "/freeze", "{}", 200);
        postJson("/api/v1/mds/operations/" + id + "/activate", "{}", 200);
        return id;
    }

    // ---------------------------------------------------------------- tests

    @Test
    @Order(1)
    void seedOperationsAndGroup() throws Exception {
        opA = createActiveOp("OP-P2-A", "P2 工序甲");
        opB = createActiveOp("OP-P2-B", "P2 工序乙");
        assertThat(opA).isNotBlank();
        assertThat(opB).isNotBlank();
        // edcPlanCode 引用守门：不存在的方案 → 2001
        assertThat(postJson("/api/v1/mds/operations", om.writeValueAsString(Map.of(
                "opCode", "OP-P2-NOPLAN", "opName", "方案缺失", "opType", "PROCESS",
                "edcPlanCode", "NO-SUCH-PLAN")), 404))
                .contains("\"code\":2001");
        // 机台组（导入指派段引用）+ 参数定义（导入参数段引用）
        postJson("/api/v1/mds/tool-groups", om.writeValueAsString(Map.of(
                "groupCode", "TG-P2", "groupName", "P2 测试组",
                "classCode", "ET-ETCH", "enabled", true)), 200);
        postJson("/api/v1/mds/param-defs", om.writeValueAsString(Map.of(
                "attrKey", "p2.max_wafers", "attrName", "P2 最大片数", "groupCode", "P2",
                "valueType", "INT", "required", false,
                "scopes", List.of("FLOW", "STEP"), "sortNo", 100)), 200);
    }

    @Test
    @Order(2)
    void generateDesignBuildsLinearCanvas() throws Exception {
        String created = postJson("/api/v1/mds/flows", om.writeValueAsString(Map.of(
                "flowName", "P2 直线流程", "flowCode", "FLP201")), 200);
        flowId = om.readTree(created).path("data").path("id").asText();

        String body = postJson("/api/v1/mds/flows/" + flowId + "/generate-design",
                om.writeValueAsString(Map.of("operationCodes", List.of("OP-P2-A", "OP-P2-B"))), 200);
        var steps = om.readTree(body).path("data").path("steps");
        assertThat(steps.size()).as("START + 2 工序 + END").isEqualTo(4);
        assertThat(steps.get(0).path("stepType").asText()).isEqualTo("START");
        assertThat(steps.get(1).path("operationCode").asText()).isEqualTo("OP-P2-A");
        assertThat(steps.get(2).path("operationCode").asText()).isEqualTo("OP-P2-B");
        assertThat(steps.get(3).path("stepType").asText()).isEqualTo("END");
        // 工序默认带出（equipType/recipeRef 随节点属性继承）
        assertThat(steps.get(1).path("equipType").asText()).isEqualTo("ET-ETCH");
        assertThat(steps.get(1).path("recipeRef").asText()).isEqualTo("R-OP-P2-A");
        var edges = om.readTree(body).path("data").path("edges");
        assertThat(edges.size()).as("直线 MAIN 全链").isEqualTo(3);
        assertThat(edges.get(0).path("edgeType").asText()).isEqualTo("MAIN");

        // 工序缺失 → 404
        postJson("/api/v1/mds/flows/" + flowId + "/generate-design",
                om.writeValueAsString(Map.of("operationCodes", List.of("OP-NO-SUCH"))), 404);
    }

    /** 三段式 xlsx 组装（表头与 FlowImportService 契约一致）。 */
    private byte[] workbook(FlowRowData... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet shFlow = wb.createSheet(FlowImportService.SHEET_FLOW);
            XSSFSheet shStep = wb.createSheet(FlowImportService.SHEET_STEP);
            XSSFSheet shAssign = wb.createSheet(FlowImportService.SHEET_ASSIGN);
            XSSFSheet shAttr = wb.createSheet(FlowImportService.SHEET_ATTR);
            header(shFlow, "flowCode*", "flowName*", "techCode", "flowKind", "stageCode",
                    "reworkOfFamily", "reworkCountLimit", "remark");
            header(shStep, "flowCode*", "nodeId*", "stepCode*", "stepName", "stepType*",
                    "operationCode", "recipeRef", "equipType", "qtimeMin", "qtimeMax",
                    "reworkLimit", "npwLimit", "batchMode", "batchSize", "holdPoint",
                    "holdReason", "inspectPlan", "samplingRate", "capabilityCode",
                    "edcPlanCode", "reticleRef", "contamLevel", "contamForbidden");
            header(shAssign, "scope*", "operationCode", "flowCode", "nodeId", "assignType*",
                    "assignRef*", "priority", "recipeRule", "recipeRef", "recipeMatch",
                    "chamberRule", "enabled");
            header(shAttr, "scope*", "operationCode", "flowCode", "nodeId", "attrKey*", "attrValue");
            for (FlowRowData d : rows) {
                d.write(shFlow, shStep, shAssign, shAttr);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void header(XSSFSheet sh, String... names) {
        Row r = sh.createRow(0);
        for (int i = 0; i < names.length; i++) {
            r.createCell(i).setCellValue(names[i]);
        }
    }

    private void row(XSSFSheet sh, String... cells) {
        int rn = sh.getLastRowNum() + 1;
        if (rn == 0 && sh.getRow(0) == null) {
            rn = 1;
        }
        Row r = sh.createRow(rn == 0 ? 1 : rn);
        for (int i = 0; i < cells.length; i++) {
            r.createCell(i).setCellValue(cells[i]);
        }
    }

    /** 惰性写四段（每行只触及涉及的 sheet）。 */
    private interface FlowRowData {
        void write(XSSFSheet flow, XSSFSheet step, XSSFSheet assign, XSSFSheet attr);
    }

    @Test
    @Order(3)
    void importExcelEndToEnd() throws Exception {
        // dryRun：新流程 + 2 节点 + 1 FLOW 指派 + 1 STEP 参数 → 0 错误
        byte[] data = workbook(
                (f, s, a, t) -> row(f, "FLP202", "导入流程", "TECH-01", "MAIN", "ST-LITHO", "", "12", ""),
                (f, s, a, t) -> row(s, "FLP202", "s1", "STP010", "导入甲", "PROCESS", "OP-P2-A", "", "", "10", "20", "", "5", "", "", "false", "", "", "", "", "", "", "", ""),
                (f, s, a, t) -> row(s, "FLP202", "s2", "STP020", "导入乙", "PROCESS", "OP-P2-B", "", "", "", "", "", "", "", "", "false", "", "", "", "", "", "", "", ""),
                (f, s, a, t) -> row(a, "FLOW", "", "FLP202", "", "GROUP", "TG-P2", "100", "GENERATE", "", "", "true"),
                (f, s, a, t) -> row(t, "STEP", "", "FLP202", "s1", "p2.max_wafers", "8"));

        FlowImportService.ImportPreview preview = importService.preview(data);
        assertThat(preview.ok()).as("dryRun 错误: %s", preview.errors()).isTrue();
        assertThat(preview.flowRows()).isEqualTo(1);
        assertThat(preview.stepRows()).isEqualTo(2);
        assertThat(preview.assignRows()).isEqualTo(1);
        assertThat(preview.attrRows()).isEqualTo(1);

        FlowImportService.ImportResult result;
        try {
            result = importService.apply(data);
        } catch (BizException e) {
            throw new AssertionError("apply 失败 key=" + e.getI18nKey()
                    + " args=" + java.util.Arrays.toString(e.getArgs()), e);
        }
        assertThat(result.flowsCreated()).isEqualTo(1);
        assertThat(result.stepsImported()).isEqualTo(2);
        assertThat(result.assignsImported()).isEqualTo(1);
        assertThat(result.attrsImported()).isEqualTo(1);

        // 库内核验：直线画布 + 主档字段 + 指派/参数落行
        String detail = getJson("/api/v1/mds/flows?keyword=FLP202");
        String newId = om.readTree(detail).path("data").get(0).path("id").asText();
        String full = getJson("/api/v1/mds/flows/" + newId);
        var root = om.readTree(full).path("data");
        assertThat(root.path("flow").path("stageCode").asText()).isEqualTo("ST-LITHO");
        assertThat(root.path("flow").path("reworkCountLimit").asInt()).isEqualTo(12);
        var steps = root.path("steps");
        assertThat(steps.size()).as("START + 2 + END").isEqualTo(4);
        assertThat(steps.get(0).path("stepType").asText()).isEqualTo("START");
        assertThat(steps.get(1).path("npwLimit").asInt()).isEqualTo(5);
        assertThat(steps.get(1).path("qtimeMin").asInt()).isEqualTo(10);
        var assigns = assignRepository().findByScopeTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_FLOW, newId);
        assertThat(assigns).hasSize(1);
        assertThat(assigns.get(0).getAssignRef()).isEqualTo("TG-P2");
        var attrs = attrRepository().findByContextTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_STEP, newId);
        assertThat(attrs).hasSize(1);
        assertThat(attrs.get(0).getStepId()).isEqualTo("s1");
        assertThat(attrs.get(0).getAttrKey()).isEqualTo("p2.max_wafers");
    }

    @Test
    @Order(4)
    void importSnapshotReplacesAndRejectsFrozen() throws Exception {
        // 快照语义：同锚点再导入 1 行指派 → 先删后插仍为 1 行（新的 ref）
        byte[] data = workbook(
                (f, s, a, t) -> row(a, "FLOW", "", "FLP202", "", "GROUP", "TG-P2", "50", "GENERATE", "", "", "true"));
        FlowImportService.ImportResult result = importService.apply(data);
        String newId = flowIdByCode("FLP202");
        var assigns = assignRepository().findByScopeTypeAndFlowIdAndDeletedFalse(
                CimEquipAssign.SCOPE_FLOW, newId);
        assertThat(assigns).as("先删后插快照").hasSize(1);
        assertThat(assigns.get(0).getPriority()).isEqualTo(50);
        assertThat(result.flowsCreated()).isEqualTo(0);
        assertThat(result.flowsUpdated()).isEqualTo(0);

        // 冻结后 dryRun 即报错（不落库）
        postJson("/api/v1/mds/flows/" + newId + "/freeze", "{}", 200);
        byte[] data2 = workbook(
                (f, s, a, t) -> row(s, "FLP202", "s9", "STP990", "冻结后", "PROCESS", "OP-P2-A", "", "", "", "", "", "", "", "false", "", "", "", "", "", "", "", ""));
        FlowImportService.ImportPreview preview = importService.preview(data2);
        assertThat(preview.ok()).as("FROZEN 应被 dryRun 拦下: %s", preview.errors()).isFalse();
        assertThat(preview.errors().stream().anyMatch(e -> e.message().contains("不可导入覆盖"))).isTrue();
    }

    private String flowIdByCode(String code) throws Exception {
        String list = getJson("/api/v1/mds/flows?keyword=" + code);
        return om.readTree(list).path("data").get(0).path("id").asText();
    }

    // 仓储直查（服务内私有；测试用 @Autowired 注入同名 bean 不便，走 mvc 反而绕——直接注入仓库）
    @Autowired
    CimEquipAssignRepository assignRepositoryInjected;
    @Autowired
    CimConstraintAttrRepository attrRepositoryInjected;

    private CimEquipAssignRepository assignRepository() {
        return assignRepositoryInjected;
    }

    private CimConstraintAttrRepository attrRepository() {
        return attrRepositoryInjected;
    }
}
