package com.cim.mds.server.flow;

import com.cim.mds.server.equipment.CimEquipment;
import com.cim.mds.server.equipment.CimEquipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 配方解算契约集成测试（V29，recipe-resolve-design.md §5）：
 * 可变参数整单维护守门（引用参数定义/仅 UNFROZEN）→ FIXED 直钉 → GENERATE 走 RMS
 * （stub）→ 各 BLOCKED 分支（VERSION_NOT_ACTIVE / RECIPE_NOT_AUTHORIZED /
 * PPID_MAPPING_INACTIVE / EXACT_MATCH_UNRESOLVED / OVERRIDE_OUT_OF_RANGE /
 * OVERRIDE_NOT_ALLOWED / AMBIGUOUS_POOL）→ validate 歧义 WARN（RECIPE_POOL_AMBIGUOUS）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(RecipeResolveTest.StubRmsConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-resolve-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecipeResolveTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired CimEquipmentRepository equipmentRepository;

    private static String opId;
    private static String flowFixed;
    private static String flowGenerate;
    private static String flowInactive;
    private static String flowMapping;
    private static String flowExact;
    private static String flowAmb;

    /** RMS stub：RCP-OK → RESOLVED；RCP-MAPPING → MAPPING_INACTIVE；其余 RECIPE_NOT_FOUND。 */
    @TestConfiguration
    static class StubRmsConfig {
        @Bean
        @Primary
        RmsRecipeClient stubRmsClient() {
            return (deviceCode, recipeCode) -> {
                if ("RCP-OK".equals(recipeCode)) {
                    return new RmsRecipeClient.Lookup("RESOLVED", "PPID-RMS-A", "CH-1",
                            3, "ACTIVE", "deadbeef", null);
                }
                if ("RCP-MAPPING".equals(recipeCode)) {
                    return new RmsRecipeClient.Lookup("MAPPING_INACTIVE", null, null,
                            null, null, null, "无机台映射");
                }
                return new RmsRecipeClient.Lookup("RECIPE_NOT_FOUND", null, null,
                        null, null, null, null);
            };
        }
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- helpers

    private String postJson(String url, String body, int expectStatus) throws Exception {
        MvcResult r = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "{}" : body))
                .andReturn();
        int s = r.getResponse().getStatus();
        String out = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (s != expectStatus) {
            throw new AssertionError("POST " + url + " expected " + expectStatus + " but was " + s + ": " + out);
        }
        return out;
    }

    private String putJson(String url, String body, int expectStatus) throws Exception {
        MvcResult r = mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "{}" : body))
                .andReturn();
        int s = r.getResponse().getStatus();
        String out = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (s != expectStatus) {
            throw new AssertionError("PUT " + url + " expected " + expectStatus + " but was " + s + ": " + out);
        }
        return out;
    }

    private String resolve(String productCode, String flowId, String nodeId,
                           String equipmentCode, List<Map<String, String>> overrides) throws Exception {
        Map<String, Object> req = new java.util.HashMap<>();
        req.put("flowId", flowId);
        req.put("nodeId", nodeId);
        req.put("equipmentCode", equipmentCode);
        if (productCode != null) req.put("productCode", productCode);
        if (overrides != null) req.put("overrides", overrides);
        return postJson("/api/v1/mds/recipe/resolve", om.writeValueAsString(req), 200);
    }

    /** 单节点画布：START → OPE010(PROCESS, 引用工序) → END。 */
    private static String canvas(String opCode) {
        return """
                <canvas version="1">
                  <nodes>
                    <node id="n1" stepCode="START" stepType="START" x="40" y="200"/>
                    <node id="n2" stepCode="OPE010" stepName="氧化" stepType="PROCESS" operationCode="%s" x="180" y="200"/>
                    <node id="n3" stepCode="END" stepType="END" x="340" y="200"/>
                  </nodes>
                  <edges>
                    <edge id="e1" from="n1" to="n2" edgeType="MAIN"/>
                    <edge id="e2" from="n2" to="n3" edgeType="MAIN"/>
                  </edges>
                </canvas>
                """.formatted(opCode);
    }

    /** 建流程+画布+候选池行（validate 前入池）+validate+activate，返回 flowId。 */
    private String newActiveFlow(String name, String opCode, List<Map<String, Object>> assigns) throws Exception {
        String body = postJson("/api/v1/mds/flows",
                om.writeValueAsString(Map.of("flowName", name)), 200);
        String flowId = om.readTree(body).path("data").path("id").asText();
        putJson("/api/v1/mds/flows/" + flowId + "/canvas",
                om.writeValueAsString(Map.of("xml", canvas(opCode))), 200);
        for (Map<String, Object> a : assigns) {
            postJson("/api/v1/mds/flows/" + flowId + "/assigns", om.writeValueAsString(a), 200);
        }
        postJson("/api/v1/mds/flows/" + flowId + "/validate", "{}", 200);
        postJson("/api/v1/mds/flows/" + flowId + "/freeze", "{}", 200);
        postJson("/api/v1/mds/flows/" + flowId + "/activate", "{}", 200);
        return flowId;
    }

    /** 建未激活流程（画布即可，validate 可不通过）。 */
    private String newInactiveFlow(String name, String opCode) throws Exception {
        String body = postJson("/api/v1/mds/flows",
                om.writeValueAsString(Map.of("flowName", name)), 200);
        String flowId = om.readTree(body).path("data").path("id").asText();
        putJson("/api/v1/mds/flows/" + flowId + "/canvas",
                om.writeValueAsString(Map.of("xml", canvas(opCode))), 200);
        return flowId;
    }

    // ---------------------------------------------------------------- tests

    @Test
    @Order(1)
    void seedOperationWithVarParams() throws Exception {
        postJson("/api/v1/mds/naming-rules",
                om.writeValueAsString(Map.of(
                        "objectType", "FLOW", "ruleName", "流程编码",
                        "segments", List.of(
                                Map.of("type", "LITERAL", "text", "FL", "length", 2),
                                Map.of("type", "SEQ", "length", 2, "start", 1, "scope", "GLOBAL")),
                        "enabled", true)), 200);
        // 参数定义（可变参数引用）
        postJson("/api/v1/mds/param-defs",
                om.writeValueAsString(Map.of(
                        "attrKey", "dc.temp", "attrName", "腔体温度",
                        "groupCode", "DC", "valueType", "DECIMAL",
                        "required", false, "scopes", List.of("OPERATION"))), 200);
        // 工序：recipeRef=RCP-OK（Logical Recipe 层）
        String body = postJson("/api/v1/mds/operations",
                om.writeValueAsString(Map.of(
                        "opCode", "OPE-M2-1", "opName", "氧化工序", "opType", "PROCESS",
                        "recipeRef", "RCP-OK")), 200);
        opId = om.readTree(body).path("data").path("id").asText();
        // 可变参数：UNFROZEN 可维护；未注册参数拒收
        putJson("/api/v1/mds/operations/" + opId + "/variable-params",
                om.writeValueAsString(List.of(Map.of("paramKey", "no.such.key"))), 400);
        String saved = putJson("/api/v1/mds/operations/" + opId + "/variable-params",
                om.writeValueAsString(List.of(Map.of(
                        "paramKey", "dc.temp", "defaultValue", "100",
                        "minValue", "90", "maxValue", "110",
                        "overrideLevel", "WAFER"))), 200);
        assertThat(saved).contains("\"defaultValue\":\"100\"").contains("\"overrideLevel\":\"WAFER\"");
        // 冻结后拒改（仅 UNFROZEN）
        postJson("/api/v1/mds/operations/" + opId + "/freeze", "{}", 200);
        putJson("/api/v1/mds/operations/" + opId + "/variable-params",
                om.writeValueAsString(List.of()), 400);
        postJson("/api/v1/mds/operations/" + opId + "/activate", "{}", 200);
        // 第二工序：recipeRef 指向 RMS 映射缺失的配方（flowMapping 专用）
        String body2 = postJson("/api/v1/mds/operations",
                om.writeValueAsString(Map.of(
                        "opCode", "OPE-M2-2", "opName", "氧化工序二", "opType", "PROCESS",
                        "recipeRef", "RCP-MAPPING")), 200);
        String opId2 = om.readTree(body2).path("data").path("id").asText();
        postJson("/api/v1/mds/operations/" + opId2 + "/freeze", "{}", 200);
        postJson("/api/v1/mds/operations/" + opId2 + "/activate", "{}", 200);
    }

    @Test
    @Order(2)
    void seedFlowsAndEquipment() throws Exception {
        // 机台（直接落库绕开位置校验链；解算只读 code/classCode/enabled）
        CimEquipment eq = new CimEquipment();
        eq.setEquipmentCode("EQP-M2-01");
        eq.setEquipmentName("氧化机台01");
        eq.setClassCode("ET-PROC");
        eq.setLocationType("FAB");
        eq.setLocationId("FAB-01");
        eq.setEnabled(true);
        equipmentRepository.save(eq);
        // flow1：FIXED
        flowFixed = newActiveFlow("FIXED 解算流程", "OPE-M2-1", List.of(Map.of(
                "assignType", "EQUIP", "assignRef", "EQP-M2-01",
                "recipeRule", "FIXED", "recipeRef", "PPID-TEST", "priority", 100)));
        // flow2：GENERATE（stub RMS）
        flowGenerate = newActiveFlow("GENERATE 解算流程", "OPE-M2-1", List.of(Map.of(
                "assignType", "EQUIP", "assignRef", "EQP-M2-01",
                "recipeRule", "GENERATE", "priority", 100)));
        // flow4：GENERATE 但 RMS 映射缺失
        flowMapping = newActiveFlow("映射缺失流程", "OPE-M2-2", List.of(Map.of(
                "assignType", "EQUIP", "assignRef", "EQP-M2-01",
                "recipeRule", "GENERATE", "priority", 100)));
        // flow5：EXACT_MATCH（recipe_match 键值域）
        flowExact = newActiveFlow("EXACT_MATCH 流程", "OPE-M2-1", List.of(Map.of(
                "assignType", "EQUIP", "assignRef", "EQP-M2-01",
                "recipeRule", "EXACT_MATCH", "recipeMatch", "{\"dc.temp\":\"100\"}",
                "priority", 100)));
        // flow3：不激活
        flowInactive = newInactiveFlow("未激活流程", "OPE-M2-1");
        assertThat(flowInactive).isNotBlank();
    }

    @Test
    @Order(3)
    void resolveFixed() throws Exception {
        String out = resolve(null, flowFixed, "n2", "EQP-M2-01", null);
        assertThat(out)
                .contains("\"resolved\":true")
                .contains("\"ppid\":\"PPID-TEST\"")
                .contains("\"ppidSource\":\"MDS_FIXED\"")
                .contains("\"recipeRule\":\"FIXED\"")
                .contains("\"logicalRecipeRef\":\"RCP-OK\"")
                .contains("\"logicalSource\":\"OPERATION_DEFAULT\"")
                .contains("\"paramKey\":\"dc.temp\"")
                .contains("\"value\":\"100\"")
                .contains("\"source\":\"DEFAULT\"");
    }

    @Test
    @Order(4)
    void resolveGenerateViaRmsStub() throws Exception {
        String out = resolve(null, flowGenerate, "n2", "EQP-M2-01", null);
        assertThat(out)
                .contains("\"resolved\":true")
                .contains("\"ppid\":\"PPID-RMS-A\"")
                .contains("\"ppidSource\":\"RMS_GENERATED\"")
                .contains("\"chamberCode\":\"CH-1\"")
                .contains("\"rmsVersionNo\":\"3\"")
                .contains("\"rmsVersionStatus\":\"ACTIVE\"")
                .contains("\"bodyHash\":\"deadbeef\"");
    }

    @Test
    @Order(5)
    void blockedBranches() throws Exception {
        // 未激活流程
        assertThat(resolve(null, flowInactive, "n2", "EQP-M2-01", null))
                .contains("\"resolved\":false").contains("VERSION_NOT_ACTIVE");
        // 机台不在授权池
        assertThat(resolve(null, flowFixed, "n2", "EQP-OTHER", null))
                .contains("\"resolved\":false").contains("RECIPE_NOT_AUTHORIZED");
        // RMS 映射缺失
        assertThat(resolve(null, flowMapping, "n2", "EQP-M2-01", null))
                .contains("\"resolved\":false").contains("PPID_MAPPING_INACTIVE");
        // EXACT_MATCH：键值命中 → RMS；未带 overrides → 未命中
        assertThat(resolve(null, flowExact, "n2", "EQP-M2-01",
                        List.of(Map.of("paramKey", "dc.temp", "value", "100"))))
                .contains("\"resolved\":true").contains("\"ppid\":\"PPID-RMS-A\"");
        assertThat(resolve(null, flowExact, "n2", "EQP-M2-01", null))
                .contains("\"resolved\":false").contains("EXACT_MATCH_UNRESOLVED");
    }

    @Test
    @Order(6)
    void variableParamGuards() throws Exception {
        // 覆盖越界
        assertThat(resolve(null, flowFixed, "n2", "EQP-M2-01",
                        List.of(Map.of("paramKey", "dc.temp", "value", "200"))))
                .contains("\"resolved\":false").contains("OVERRIDE_OUT_OF_RANGE");
        // 覆盖未声明可变的参数
        assertThat(resolve(null, flowFixed, "n2", "EQP-M2-01",
                        List.of(Map.of("paramKey", "unknown.key", "value", "1"))))
                .contains("\"resolved\":false").contains("OVERRIDE_NOT_ALLOWED");
        // 合法覆盖取值生效
        assertThat(resolve(null, flowFixed, "n2", "EQP-M2-01",
                        List.of(Map.of("paramKey", "dc.temp", "value", "105"))))
                .contains("\"resolved\":true")
                .contains("\"value\":\"105\"")
                .contains("\"source\":\"OVERRIDE\"");
    }

    @Test
    @Order(7)
    void ambiguousPoolAndValidateWarn() throws Exception {
        // 新建 UNFROZEN 流程：同优先级压两条配方口径不同的 FIXED 行
        String flowId = newInactiveFlow("歧义池流程", "OPE-M2-1");
        for (String ppid : List.of("PPID-AMB-A", "PPID-AMB-B")) {
            postJson("/api/v1/mds/flows/" + flowId + "/assigns",
                    om.writeValueAsString(Map.of(
                            "assignType", "EQUIP", "assignRef", "EQP-M2-01",
                            "recipeRule", "FIXED", "recipeRef", ppid, "priority", 100)), 200);
        }
        // validate 产生 WARN（不阻断激活）
        String validate = postJson("/api/v1/mds/flows/" + flowId + "/validate", "{}", 200);
        assertThat(validate).contains("RECIPE_POOL_AMBIGUOUS");
        postJson("/api/v1/mds/flows/" + flowId + "/freeze", "{}", 200);
        postJson("/api/v1/mds/flows/" + flowId + "/activate", "{}", 200);
        // 解算同桶两口径 → AMBIGUOUS_POOL 硬拦
        assertThat(resolve(null, flowId, "n2", "EQP-M2-01", null))
                .contains("\"resolved\":false").contains("AMBIGUOUS_POOL");
    }
}
