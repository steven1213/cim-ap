package com.cim.mds.server.edc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.poi.ss.usermodel.Row;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EDC 工程数采（V28）集成测试：参数定义 unit 扩展、参数集/成员守门、
 * 设备接口映射守门、方案四态生命周期（activate 降级同族旧 ACTIVE）、
 * 明细编辑只读闸、Excel 模板/导入闭环。
 *
 * <p>认证关闭直通；独立 H2 库名（各测类隔离）。业务码断言按 {@code $.code}
 * （BizCode→HTTP：2000→400、2001→404、2003→409、2004→400）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-edc-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EdcAdminTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String paramP1;
    private static String paramP2;
    private static String setId;
    private static String tplId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- helpers

    private String postJson(String url, String body, int expectStatus) throws Exception {
        MvcResult result = mvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        int status = result.getResponse().getStatus();
        String out = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (status != expectStatus) {
            throw new AssertionError("POST " + url + " expected " + expectStatus
                    + " but was " + status + ": " + out);
        }
        return out;
    }

    private String putJson(String url, String body, int expectStatus) throws Exception {
        MvcResult result = mvc.perform(put(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        int status = result.getResponse().getStatus();
        String out = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
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

    private String createParamDef(String key, String unit) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("attrKey", key);
        m.put("attrName", "参数 " + key);
        m.put("groupCode", "PROCESS");
        m.put("valueType", "DECIMAL");
        m.put("unit", unit);
        m.put("required", false);
        m.put("scopes", List.of("OPERATION", "FLOW", "STEP"));
        return postJson("/api/v1/mds/param-defs", om.writeValueAsString(m), 200);
    }

    // ---------------------------------------------------------------- cases

    @Test
    @Order(1)
    void paramDefSupportsUnit() throws Exception {
        String created = createParamDef("P-GAS", "sccm");
        assertThat(created).contains("\"unit\":\"sccm\"");
        // 重复键 → 2003
        String dup = postJson("/api/v1/mds/param-defs", om.writeValueAsString(Map.of(
                "attrKey", "P-GAS", "attrName", "x", "groupCode", "PROCESS",
                "valueType", "STRING", "scopes", List.of("STEP"))), 409);
        assertThat(om.readTree(dup).path("code").asInt()).isEqualTo(2003);
        paramP1 = om.readTree(created).path("data").path("id").asText();
        paramP2 = om.readTree(createParamDef("P-PRESS", "Torr")).path("data").path("id").asText();
    }

    @Test
    @Order(2)
    void setCrudItemGuards() throws Exception {
        // 建集 → 重复 → 2003
        String created = postJson("/api/v1/mds/edc/param-sets", om.writeValueAsString(Map.of(
                "setCode", "SET-ETCH", "setName", "刻蚀工序参数集", "opCode", "OP-EDC-A")), 200);
        setId = om.readTree(created).path("data").path("id").asText();
        String dup = postJson("/api/v1/mds/edc/param-sets", om.writeValueAsString(Map.of(
                "setCode", "SET-ETCH", "setName", "x")), 409);
        assertThat(om.readTree(dup).path("code").asInt()).isEqualTo(2003);

        // 未知参数键守门 → 2000
        String bad = putJson("/api/v1/mds/edc/param-sets/" + setId + "/items",
                om.writeValueAsString(List.of(Map.of("attrKey", "NO-SUCH-KEY"))), 400);
        assertThat(om.readTree(bad).path("code").asInt()).isEqualTo(2000);

        // 整单替换 + 排序
        putJson("/api/v1/mds/edc/param-sets/" + setId + "/items",
                om.writeValueAsString(List.of(
                        Map.of("attrKey", "P-PRESS", "sortNo", 2),
                        Map.of("attrKey", "P-GAS", "sortNo", 1))), 200);
        String view = getJson("/api/v1/mds/edc/param-sets/" + setId);
        assertThat(view).contains("\"P-GAS\"").contains("\"P-PRESS\"").contains("\"unit\":\"sccm\"");
    }

    @Test
    @Order(3)
    void reportTplAndMapGuards() throws Exception {
        // 建模板 → 重复 → 2003
        String created = postJson("/api/v1/mds/edc/report-tpls", om.writeValueAsString(Map.of(
                "tplCode", "RPT-1", "tplName", "测试报告")), 200);
        tplId = om.readTree(created).path("data").path("id").asText();
        String dup = postJson("/api/v1/mds/edc/report-tpls", om.writeValueAsString(Map.of(
                "tplCode", "RPT-1", "tplName", "x")), 409);
        assertThat(om.readTree(dup).path("code").asInt()).isEqualTo(2003);

        // 成员守门：未知参数 → 2000；SVID 非法 → 2000；SVID 重复 → 2004
        assertThat(putJson("/api/v1/mds/edc/report-tpls/" + tplId + "/items",
                om.writeValueAsString(List.of(Map.of("svid", 1001, "attrKey", "NO-SUCH-KEY"))), 400))
                .contains("\"code\":2000");
        assertThat(putJson("/api/v1/mds/edc/report-tpls/" + tplId + "/items",
                om.writeValueAsString(List.of(Map.of("svid", 0, "attrKey", "P-GAS"))), 400))
                .contains("\"code\":2000");
        assertThat(putJson("/api/v1/mds/edc/report-tpls/" + tplId + "/items",
                om.writeValueAsString(List.of(
                        Map.of("svid", 1001, "attrKey", "P-GAS"),
                        Map.of("svid", 1001, "attrKey", "P-PRESS"))), 400))
                .contains("\"code\":2004");

        // 合法成员整单替换（SVID→参数视图带单位）
        putJson("/api/v1/mds/edc/report-tpls/" + tplId + "/items",
                om.writeValueAsString(List.of(
                        Map.of("svid", 1002, "attrKey", "P-PRESS", "sortNo", 2),
                        Map.of("svid", 1001, "attrKey", "P-GAS", "sortNo", 1))), 200);
        String view = getJson("/api/v1/mds/edc/report-tpls/" + tplId);
        assertThat(view).contains("\"svid\":1001").contains("\"P-GAS\"").contains("\"unit\":\"sccm\"");

        // 绑定守门：未知模板 → 2001；重复绑定 → 2003；非法通道 → 2000；停用模板 → 2000
        assertThat(postJson("/api/v1/mds/edc/equip-maps", om.writeValueAsString(Map.of(
                "equipmentCode", "ETCH-01", "ceid", 1001, "tplId", "NO-TPL")), 404))
                .contains("\"code\":2001");
        String mapBody = om.writeValueAsString(Map.of(
                "equipmentCode", "ETCH-01", "ceid", 1001, "tplId", tplId, "sourceChannel", "GEM"));
        postJson("/api/v1/mds/edc/equip-maps", mapBody, 200);
        assertThat(postJson("/api/v1/mds/edc/equip-maps", mapBody, 409))
                .contains("\"code\":2003");
        assertThat(postJson("/api/v1/mds/edc/equip-maps", om.writeValueAsString(Map.of(
                "equipmentCode", "ETCH-01", "ceid", 1002, "tplId", tplId, "sourceChannel", "XX")), 400))
                .contains("\"code\":2000");
        String off = postJson("/api/v1/mds/edc/report-tpls", om.writeValueAsString(Map.of(
                "tplCode", "RPT-OFF", "tplName", "停用模板")), 200);
        String offId = om.readTree(off).path("data").path("id").asText();
        putJson("/api/v1/mds/edc/report-tpls/" + offId,
                om.writeValueAsString(Map.of("tplName", "停用模板", "enabled", false)), 200);
        assertThat(postJson("/api/v1/mds/edc/equip-maps", om.writeValueAsString(Map.of(
                "equipmentCode", "ETCH-01", "ceid", 1003, "tplId", offId)), 400))
                .contains("\"code\":2000");

        // 模板被绑定 → 删除守门 2004
        MvcResult delResult = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/mds/edc/report-tpls/" + tplId))
                .andReturn();
        assertThat(delResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":2004");
    }

    @Test
    @Order(4)
    void planLifecycleAndGuards() throws Exception {
        // 前置主数据：ACTIVE 工序 + UNFROZEN 流程版本
        String opBody = postJson("/api/v1/mds/operations", om.writeValueAsString(Map.of(
                "opCode", "OP-EDC-A", "opName", "刻蚀", "opType", "PROCESS",
                "equipType", "ET-ETCH")), 200);
        String opId = om.readTree(opBody).path("data").path("id").asText();
        postJson("/api/v1/mds/operations/" + opId + "/freeze", "{}", 200);
        postJson("/api/v1/mds/operations/" + opId + "/activate", "{}", 200);
        postJson("/api/v1/mds/flows", om.writeValueAsString(Map.of(
                "flowName", "EDC 测试流程", "flowCode", "FLP-EDC1")), 200);

        // 绑定存在性守门
        assertThat(postJson("/api/v1/mds/edc/plans", om.writeValueAsString(Map.of(
                "planCode", "PLAN-EDC", "flowFamilyCode", "FLP-EDC1", "flowVersionNo", 1,
                "opCode", "OP-EDC-A", "productCode", "NO-PRODUCT")), 404))
                .contains("\"code\":2001");
        assertThat(postJson("/api/v1/mds/edc/plans", om.writeValueAsString(Map.of(
                "planCode", "PLAN-EDC", "flowFamilyCode", "NO-FLOW", "flowVersionNo", 1,
                "opCode", "OP-EDC-A")), 404))
                .contains("\"code\":2001");
        assertThat(postJson("/api/v1/mds/edc/plans", om.writeValueAsString(Map.of(
                "planCode", "PLAN-EDC", "flowFamilyCode", "FLP-EDC1", "flowVersionNo", 1,
                "opCode", "NO-OP")), 404))
                .contains("\"code\":2001");

        // V1 建成（UNFROZEN）
        String v1 = postJson("/api/v1/mds/edc/plans", om.writeValueAsString(Map.of(
                "planCode", "PLAN-EDC", "flowFamilyCode", "FLP-EDC1", "flowVersionNo", 1,
                "opCode", "OP-EDC-A")), 200);
        String v1Id = om.readTree(v1).path("data").path("id").asText();

        // 无明细不能冻结 → 2004
        assertThat(postJson("/api/v1/mds/edc/plans/" + v1Id + "/freeze", "{}", 400))
                .contains("\"code\":2004");

        // BY_TIME 无间隔 → 2000；LSL>=USL → 2000
        assertThat(putJson("/api/v1/mds/edc/plans/" + v1Id + "/items",
                om.writeValueAsString(List.of(Map.of("attrKey", "P-GAS", "sampleRule", "BY_TIME"))), 400))
                .contains("\"code\":2000");
        assertThat(putJson("/api/v1/mds/edc/plans/" + v1Id + "/items",
                om.writeValueAsString(List.of(Map.of("attrKey", "P-GAS",
                        "lsl", 110, "usl", 90))), 400))
                .contains("\"code\":2000");

        // 合法明细 → 参数集一键带入（replace 默认属性并标注来源）→ 冻结 → 激活
        putJson("/api/v1/mds/edc/plans/" + v1Id + "/items",
                om.writeValueAsString(List.of(Map.of(
                        "attrKey", "P-GAS", "collectMode", "AUTO", "sampleRule", "BY_TIME",
                        "intervalMinutes", 30, "required", true,
                        "target", 100, "lsl", 90, "usl", 110, "oocAction", "HOLD"))), 200);
        postJson("/api/v1/mds/edc/plans/" + v1Id + "/apply-set?setCode=SET-ETCH", "{}", 200);
        String v1View = getJson("/api/v1/mds/edc/plans/" + v1Id);
        assertThat(v1View).contains("\"setCode\":\"SET-ETCH\"").contains("\"P-PRESS\"");
        postJson("/api/v1/mds/edc/plans/" + v1Id + "/freeze", "{}", 200);
        postJson("/api/v1/mds/edc/plans/" + v1Id + "/activate", "{}", 200);

        // ACTIVE 明细只读 → 2004
        assertThat(putJson("/api/v1/mds/edc/plans/" + v1Id + "/items",
                om.writeValueAsString(List.of(Map.of("attrKey", "P-GAS"))), 400))
                .contains("\"code\":2004");

        // V2 自动版本 max+1 → 冻结 → 激活降级 V1
        String v2 = postJson("/api/v1/mds/edc/plans", om.writeValueAsString(Map.of(
                "planCode", "PLAN-EDC", "flowFamilyCode", "FLP-EDC1", "flowVersionNo", 1,
                "opCode", "OP-EDC-A")), 200);
        assertThat(om.readTree(v2).path("data").path("versionNo").asInt()).isEqualTo(2);
        String v2Id = om.readTree(v2).path("data").path("id").asText();
        assertThat(postJson("/api/v1/mds/edc/plans/" + v2Id + "/freeze", "{}", 400))
                .contains("\"code\":2004"); // V2 尚无明细
        putJson("/api/v1/mds/edc/plans/" + v2Id + "/items",
                om.writeValueAsString(List.of(Map.of("attrKey", "P-PRESS"))), 200);
        postJson("/api/v1/mds/edc/plans/" + v2Id + "/freeze", "{}", 200);
        postJson("/api/v1/mds/edc/plans/" + v2Id + "/activate", "{}", 200);
        String list = getJson("/api/v1/mds/edc/plans");
        assertThat(list).contains("\"versionNo\":1").contains("\"versionNo\":2");
        // V1 已降级 FROZEN，V2 ACTIVE
        assertThat(list).contains("\"status\":\"FROZEN\"").contains("\"status\":\"ACTIVE\"");
        assertThat(getJson("/api/v1/mds/edc/plans/" + v1Id)).contains("\"status\":\"FROZEN\"");

        // 参数集被方案引用 → 删除守门 2004
        MvcResult delResult = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/mds/edc/param-sets/" + setId))
                .andReturn();
        assertThat(delResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"code\":2004");
    }

    @Test
    @Order(5)
    void importTemplateAndRoundtrip() throws Exception {
        // 模板下载（含示例行）→ 原样导入 PARAM_DEF → 示例行 upsert 成功
        String tpl = getJson("/api/v1/mds/edc/import-template?kind=PARAM_DEF");
        String b64 = om.readTree(tpl).path("data").asText();
        String report = postJson("/api/v1/mds/edc/import", om.writeValueAsString(Map.of(
                "kind", "PARAM_DEF", "dataBase64", b64)), 200);
        assertThat(report).contains("\"success\":1").contains("\"failed\":0");

        // 参数集成员导入（自建 xlsx：一好一坏行，验证逐行报告）
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sh = wb.createSheet("items");
            Row head = sh.createRow(0);
            head.createCell(0).setCellValue("attr_key");
            head.createCell(1).setCellValue("sort_no");
            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue("P-GAS");
            r1.createCell(1).setCellValue(5);
            Row r2 = sh.createRow(2);
            r2.createCell(0).setCellValue("NO-SUCH");
            wb.write(out);
            xlsx = out.toByteArray();
        }
        String itemReport = postJson("/api/v1/mds/edc/import", om.writeValueAsString(Map.of(
                "kind", "PARAM_SET_ITEM", "dataBase64", Base64.getEncoder().encodeToString(xlsx),
                "targetId", setId)), 200);
        assertThat(itemReport).contains("\"total\":2").contains("\"success\":1").contains("\"failed\":1");

        // 方案明细导入：FROZEN 方案整行报错（状态守门），UNFROZEN 落行
        String plans = getJson("/api/v1/mds/edc/plans");
        String frozenId = null;
        for (var node : om.readTree(plans).path("data")) {
            if ("FROZEN".equals(node.path("status").asText())) {
                frozenId = node.path("id").asText();
                break;
            }
        }
        byte[] planXlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sh = wb.createSheet("plan_items");
            Row head = sh.createRow(0);
            String[] headers = {"attr_key", "collect_mode", "sample_rule", "interval_minutes",
                    "required", "target", "lsl", "usl", "ooc_action", "sort_no"};
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
            }
            Row r = sh.createRow(1);
            String[] vals = {"P-GAS", "AUTO", "BY_LOT", "", "TRUE", "100", "90", "110", "HOLD", "1"};
            for (int i = 0; i < vals.length; i++) {
                r.createCell(i).setCellValue(vals[i]);
            }
            wb.write(out);
            planXlsx = out.toByteArray();
        }
        String b64Plan = Base64.getEncoder().encodeToString(planXlsx);
        assertThat(postJson("/api/v1/mds/edc/import", om.writeValueAsString(Map.of(
                "kind", "PLAN_ITEM", "dataBase64", b64Plan, "targetId", frozenId)), 200))
                .contains("\"success\":0").contains("\"failed\":1");

        // 报告模板成员导入（一好一坏行；upsert by svid）
        byte[] rptXlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sh = wb.createSheet("report_tpl_items");
            Row head = sh.createRow(0);
            head.createCell(0).setCellValue("svid");
            head.createCell(1).setCellValue("attr_key");
            head.createCell(2).setCellValue("sort_no");
            Row r1 = sh.createRow(1);
            r1.createCell(0).setCellValue(1005);
            r1.createCell(1).setCellValue("P-GAS");
            r1.createCell(2).setCellValue(1);
            Row r2 = sh.createRow(2);
            r2.createCell(0).setCellValue(1006);
            r2.createCell(1).setCellValue("NO-SUCH");
            wb.write(out);
            rptXlsx = out.toByteArray();
        }
        assertThat(postJson("/api/v1/mds/edc/import", om.writeValueAsString(Map.of(
                "kind", "REPORT_ITEM", "dataBase64", Base64.getEncoder().encodeToString(rptXlsx),
                "targetId", tplId)), 200))
                .contains("\"total\":2").contains("\"success\":1").contains("\"failed\":1");

        // 设备映射导入（模板格式）：模板示例行原样导入（tpl_code=RPT-ETCH-PROC 种子模板）→ upsert 成功
        String mapTpl = getJson("/api/v1/mds/edc/import-template?kind=EQUIP_MAP");
        assertThat(postJson("/api/v1/mds/edc/import", om.writeValueAsString(Map.of(
                "kind", "EQUIP_MAP", "dataBase64", om.readTree(mapTpl).path("data").asText())), 200))
                .contains("\"success\":1").contains("\"failed\":0");
    }
}
