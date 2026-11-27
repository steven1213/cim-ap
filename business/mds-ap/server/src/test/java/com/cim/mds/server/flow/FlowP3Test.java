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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流程域 P3 集成测试（V24）：事件历史（拍板项 D）——生命周期动作（CREATE/SAVE_XML/
 * FREEZE/ACTIVATE/DISABLE）、覆盖数据变更（指派创建/KV 替换）、Excel 导入 IMPORT 标记；
 * 读端点时间倒序 + actor 落行。
 *
 * <p>认证关闭直通；独立 H2 库名（各测类隔离）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-p3-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowP3Test {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    FlowImportService importService;
    @Autowired
    FlowEventService flowEventService;

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

    private String putJson(String url, String body, int expectStatus) throws Exception {
        MvcResult r = mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "{}" : body))
                .andReturn();
        int status = r.getResponse().getStatus();
        String out = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (status != expectStatus) {
            throw new AssertionError("PUT " + url + " expected " + expectStatus
                    + " but was " + status + ": " + out);
        }
        return out;
    }

    private String getJson(String url) throws Exception {
        MvcResult r = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String createActiveOp(String code, String name) throws Exception {
        String body = postJson("/api/v1/mds/operations", om.writeValueAsString(Map.of(
                "opCode", code, "opName", name, "opType", "PROCESS",
                "equipType", "ET-ETCH", "recipeRef", "R-" + code)), 200);
        String id = om.readTree(body).path("data").path("id").asText();
        postJson("/api/v1/mds/operations/" + id + "/freeze", "{}", 200);
        postJson("/api/v1/mds/operations/" + id + "/activate", "{}", 200);
        return id;
    }

    /** 惰性写四段（每行只触及涉及的 sheet）。 */
    private interface FlowRowData {
        void write(XSSFSheet flow, XSSFSheet step, XSSFSheet assign, XSSFSheet attr);
    }

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

    private String flowIdByCode(String code) throws Exception {
        String list = getJson("/api/v1/mds/flows?keyword=" + code);
        return om.readTree(list).path("data").get(0).path("id").asText();
    }

    private List<com.fasterxml.jackson.databind.JsonNode> eventData(String id) throws Exception {
        String body = getJson("/api/v1/mds/flows/" + id + "/events");
        List<com.fasterxml.jackson.databind.JsonNode> out = new java.util.ArrayList<>();
        om.readTree(body).path("data").forEach(out::add);
        return out;
    }

    private List<String> eventActions(String id) throws Exception {
        return eventData(id).stream()
                .map(n -> n.path("action").asText())
                .collect(java.util.stream.Collectors.toList());
    }

    // ---------------------------------------------------------------- tests

    @Test
    @Order(1)
    void seed() throws Exception {
        createActiveOp("OP-P3-A", "P3 工序甲");
        postJson("/api/v1/mds/tool-groups", om.writeValueAsString(Map.of(
                "groupCode", "TG-P3", "groupName", "P3 测试组",
                "classCode", "ET-ETCH", "enabled", true)), 200);
        postJson("/api/v1/mds/param-defs", om.writeValueAsString(Map.of(
                "attrKey", "p3.key", "attrName", "P3 参数", "groupCode", "P3",
                "valueType", "STRING", "required", false,
                "scopes", List.of("FLOW", "PRODUCT"), "sortNo", 100)), 200);
    }

    @Test
    @Order(2)
    void lifecycleWritesEvents() throws Exception {
        // CREATE
        String created = postJson("/api/v1/mds/flows", om.writeValueAsString(Map.of(
                "flowName", "P3 事件流程", "flowCode", "FLP301")), 200);
        flowId = om.readTree(created).path("data").path("id").asText();

        // SAVE_XML（经 generateDesign → saveXml 复用同一守门路径）
        postJson("/api/v1/mds/flows/" + flowId + "/generate-design",
                om.writeValueAsString(Map.of("operationCodes", List.of("OP-P3-A"))), 200);

        // 覆盖数据变更：FLOW 指派 + KV 替换
        postJson("/api/v1/mds/flows/" + flowId + "/assigns", om.writeValueAsString(Map.of(
                "assignType", "GROUP", "assignRef", "TG-P3", "priority", 100,
                "recipeRule", "GENERATE", "enabled", true)), 200);
        putJson("/api/v1/mds/flows/" + flowId + "/attrs", om.writeValueAsString(Map.of(
                "values", Map.of("p3.key", "v1"))), 200);

        // 生命周期：validate → freeze → activate → disable
        postJson("/api/v1/mds/flows/" + flowId + "/validate", "{}", 200);
        postJson("/api/v1/mds/flows/" + flowId + "/freeze", "{}", 200);
        postJson("/api/v1/mds/flows/" + flowId + "/activate", "{}", 200);
        postJson("/api/v1/mds/flows/" + flowId + "/disable", "{}", 200);

        // 读端点：时间倒序包含全部动作；CREATE 事件 detail 带 flowName
        List<String> actions = eventActions(flowId);
        assertThat(actions).as("倒序动作链: %s", actions).containsSubsequence(
                "DISABLE", "ACTIVATE", "FREEZE", "ATTR_REPLACE", "SAVE_XML", "CREATE");
        assertThat(actions.stream().filter("CREATE"::equals).count()).isEqualTo(2); // flow + assign
        assertThat(actions.stream().filter("SAVE_XML"::equals).count())
                .as("generateDesign 落一次 SAVE_XML").isEqualTo(1);

        String body = getJson("/api/v1/mds/flows/" + flowId + "/events");
        var first = om.readTree(body).path("data").get(0);
        assertThat(first.path("action").asText()).isEqualTo("DISABLE");
        assertThat(first.path("flowCode").asText()).isEqualTo("FLP301");
        assertThat(first.path("actor").asText()).isNotBlank();

        var createEvt = eventData(flowId).stream()
                .filter(n -> "FLOW".equals(n.path("entityType").asText())
                        && "CREATE".equals(n.path("action").asText()))
                .findFirst().orElseThrow();
        assertThat(createEvt.path("detail").asText()).contains("P3 事件流程");
        assertThat(createEvt.path("entityType").asText()).isEqualTo("FLOW");
    }

    @Test
    @Order(3)
    void importWritesImportEvent() throws Exception {
        // FLP301 在 Order(2) 已 DISABLE（导入只读守门拦下）——更新目标改用先建的 UNFROZEN 流程
        String created = postJson("/api/v1/mds/flows", om.writeValueAsString(Map.of(
                "flowName", "P3 预建流程", "flowCode", "FLP303")), 200);
        String id3 = om.readTree(created).path("data").path("id").asText();

        // 导入：FLP303 改名（UPDATE）+ FLP302 新建（CREATE）；两者都落 IMPORT 标记
        byte[] data = workbook(
                (f, s, a, t) -> row(f, "FLP303", "P3 预建流程改名", "TECH-01", "MAIN", "", "", "", ""),
                (f, s, a, t) -> row(f, "FLP302", "P3 导入流程", "TECH-01", "MAIN", "", "", "", ""));
        FlowImportService.ImportResult result;
        try {
            result = importService.apply(data);
        } catch (BizException e) {
            throw new AssertionError("apply 失败 key=" + e.getI18nKey()
                    + " args=" + java.util.Arrays.toString(e.getArgs()), e);
        }
        assertThat(result.flowsCreated()).isEqualTo(1);
        assertThat(result.flowsUpdated()).isEqualTo(1);

        assertThat(eventActions(id3)).containsSubsequence("IMPORT", "UPDATE");
        String id2 = flowIdByCode("FLP302");
        List<String> actions2 = eventActions(id2);
        assertThat(actions2).as("新建流程导入事件链: %s", actions2).containsSubsequence("IMPORT", "CREATE");
    }

    @Test
    @Order(4)
    void assignUpdateAndDeleteWriteEvents() throws Exception {
        String id = flowIdByCode("FLP302");
        // FLP302 无画布（只有主档行）：UPD/DEL 指派走 FLOW 级
        String created = postJson("/api/v1/mds/flows/" + id + "/assigns", om.writeValueAsString(Map.of(
                "assignType", "GROUP", "assignRef", "TG-P3", "priority", 100,
                "recipeRule", "GENERATE", "enabled", true)), 200);
        String assignId = om.readTree(created).path("data").path("id").asText();

        putJson("/api/v1/mds/assigns/" + assignId, om.writeValueAsString(Map.of(
                "assignType", "GROUP", "assignRef", "TG-P3", "priority", 50,
                "recipeRule", "GENERATE", "enabled", true)), 200);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/mds/assigns/" + assignId))
                .andExpect(status().isOk());

        List<String> actions = eventActions(id);
        assertThat(actions).as("指派 U/D 事件链: %s", actions)
                .containsSubsequence("DELETE", "UPDATE", "CREATE");
        assertThat(actions.stream().filter("DELETE"::equals).count()).isEqualTo(1);
    }

    @Test
    @Order(5)
    void productOverrideL3() throws Exception {
        // 产品主数据（绑定守门要求产品存在）
        postJson("/api/v1/mds/products", om.writeValueAsString(Map.of(
                "productCode", "P-001", "productName", "P3 测试产品")), 200);
        // 建一条 ACTIVE 流程（绑定守门要求 ACTIVE / FROZEN+VALID）
        String created = postJson("/api/v1/mds/flows", om.writeValueAsString(Map.of(
                "flowName", "P3 产品轴流程", "flowCode", "FLP304")), 200);
        String flowIdL3 = om.readTree(created).path("data").path("id").asText();
        postJson("/api/v1/mds/flows/" + flowIdL3 + "/generate-design",
                om.writeValueAsString(Map.of("operationCodes", List.of("OP-P3-A"))), 200);
        postJson("/api/v1/mds/flows/" + flowIdL3 + "/assigns", om.writeValueAsString(Map.of(
                "assignType", "GROUP", "assignRef", "TG-P3", "priority", 100,
                "recipeRule", "GENERATE", "enabled", true)), 200);
        postJson("/api/v1/mds/flows/" + flowIdL3 + "/validate", "{}", 200);
        postJson("/api/v1/mds/flows/" + flowIdL3 + "/freeze", "{}", 200);
        postJson("/api/v1/mds/flows/" + flowIdL3 + "/activate", "{}", 200);

        // 绑定（family = FLP304 自成版本族）→ 列型覆盖 + PRODUCT 指派 + PRODUCT KV
        String bindBody = postJson("/api/v1/mds/product-flows", om.writeValueAsString(Map.of(
                "productCode", "P-001", "flowFamilyCode", "FLP304", "flowVersionNo", 1)), 200);
        String bindId = om.readTree(bindBody).path("data").path("id").asText();

        String upd = putJson("/api/v1/mds/product-flows/" + bindId, om.writeValueAsString(Map.of(
                "reticleRef", "RT-LT-001", "contamLevel", "AL", "contamForbidden", "HF", "npwLimit", 8)), 200);
        assertThat(om.readTree(upd).path("data").path("reticleRef").asText()).isEqualTo("RT-LT-001");
        assertThat(om.readTree(upd).path("data").path("npwLimit").asInt()).isEqualTo(8);

        String pa = postJson("/api/v1/mds/product-flows/" + bindId + "/assigns", om.writeValueAsString(Map.of(
                "assignType", "GROUP", "assignRef", "TG-P3", "priority", 50,
                "recipeRule", "GENERATE", "enabled", true)), 200);
        assertThat(om.readTree(pa).path("data").path("scopeType").asText()).isEqualTo("PRODUCT");

        putJson("/api/v1/mds/product-flows/" + bindId + "/attrs", om.writeValueAsString(Map.of(
                "values", Map.of("p3.key", "pv"))), 200);

        // 按产品解析：PRODUCT 层生效（列型 > 节点；KV > 三层；池 > 三层）
        String body = getJson("/api/v1/mds/flows/" + flowIdL3 + "/effective?product=P-001");
        var node0 = om.readTree(body).path("data").get(0);
        assertThat(node0.path("reticleRef").path("value").asText()).isEqualTo("RT-LT-001");
        assertThat(node0.path("reticleRef").path("source").asText()).isEqualTo("PRODUCT_OVERRIDE");
        assertThat(node0.path("contamLevel").path("value").asText()).isEqualTo("AL");
        assertThat(node0.path("npwLimit").path("value").asInt()).isEqualTo(8);
        assertThat(node0.path("attrs").path("p3.key").path("value").asText()).isEqualTo("pv");
        assertThat(node0.path("attrs").path("p3.key").path("source").asText()).isEqualTo("PRODUCT_OVERRIDE");
        assertThat(node0.path("poolSource").asText()).isEqualTo("PRODUCT_OVERRIDE");

        // 主数据口径（不带 product）：PRODUCT 覆盖不出现
        String body2 = getJson("/api/v1/mds/flows/" + flowIdL3 + "/effective");
        var node0p = om.readTree(body2).path("data").get(0);
        assertThat(node0p.path("reticleRef").path("source").asText())
                .as("主数据口径无产品覆盖").isNotEqualTo("PRODUCT_OVERRIDE");
        assertThat(node0p.path("attrs").path("p3.key").path("value").isNull()).isTrue();
        assertThat(node0p.path("poolSource").asText()).isEqualTo("FLOW_OVERRIDE");
    }

    @Test
    @Order(6)
    void npwProductBindRejected() throws Exception {
        // V26 定稿：NPW 用途产品不挂产品流程（绑定守门 → OPERATION_NOT_ALLOWED=2004/400）
        postJson("/api/v1/mds/products", om.writeValueAsString(Map.of(
                "productCode", "NPW-P3", "productName", "P3 控挡陪片",
                "usageType", "NPW", "npwType", "MONITOR")), 200);
        String body = postJson("/api/v1/mds/product-flows", om.writeValueAsString(Map.of(
                "productCode", "NPW-P3", "flowFamilyCode", "FLP304", "flowVersionNo", 1)), 400);
        assertThat(om.readTree(body).path("code").asInt()).isEqualTo(2004);
    }

    @Test
    @Order(7)
    void recipeConsistentWarnOnFixedMismatch() throws Exception {
        // RECIPE_CONSISTENT（WARN）：节点生效配方 = 工序默认 R-OP-P3-A（createActiveOp），
        // 候选池 FIXED 行锁 R-OTHER → 派工预检口径告警；GENERATE 行不触发
        String created = postJson("/api/v1/mds/flows", om.writeValueAsString(Map.of(
                "flowName", "P3 配方一致性流程", "flowCode", "FLP305")), 200);
        String id5 = om.readTree(created).path("data").path("id").asText();
        postJson("/api/v1/mds/flows/" + id5 + "/generate-design",
                om.writeValueAsString(Map.of("operationCodes", List.of("OP-P3-A"))), 200);
        postJson("/api/v1/mds/flows/" + id5 + "/assigns", om.writeValueAsString(Map.of(
                "assignType", "GROUP", "assignRef", "TG-P3", "priority", 100,
                "recipeRule", "FIXED", "recipeRef", "R-OTHER", "enabled", true)), 200);
        String body = postJson("/api/v1/mds/flows/" + id5 + "/validate", "{}", 200);
        assertThat(om.readTree(body).path("data").path("problems").toString())
                .contains("[WARN/RECIPE_CONSISTENT]").contains("R-OTHER").contains("R-OP-P3-A");

        // 节点级覆盖与 FIXED 行一致 → 告警消失
        putJson("/api/v1/mds/assigns/" + lastAssignId(id5), om.writeValueAsString(Map.of(
                "assignType", "GROUP", "assignRef", "TG-P3", "priority", 100,
                "recipeRule", "FIXED", "recipeRef", "R-OP-P3-A", "enabled", true)), 200);
        String body2 = postJson("/api/v1/mds/flows/" + id5 + "/validate", "{}", 200);
        assertThat(om.readTree(body2).path("data").path("problems").toString())
                .doesNotContain("RECIPE_CONSISTENT");
    }

    /** 取流程最近一条指派行 id（测试内帮手）。 */
    private String lastAssignId(String flowId) throws Exception {
        String list = getJson("/api/v1/mds/flows/" + flowId + "/assigns");
        return om.readTree(list).path("data").get(0).path("id").asText();
    }
}
