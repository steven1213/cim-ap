package com.cim.mds.server.flow;

import com.fasterxml.jackson.databind.ObjectMapper;

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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流程独立规则（V15）集成测试：CRUD 守门 + 版本只读 + 条件微语法 + evaluate 契约
 * + 内置模板（Q-time 超限挂起 / Bin 条件选路）。
 *
 * <p>认证关闭直通；独立 H2 库名（各测类隔离）。业务码断言按 {@code $.code}
 * （BizCode→HTTP：2000→400、2001→404、2003→409、2004→400）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-flowrule-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowRuleTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String flowId;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- helpers

    /** 画布：标准链 + 带条件选路边（n4→n5 PASS bin 条件）+ 一条跨站 Q-time。 */
    private static final String XML = """
            <canvas version="1">
              <nodes>
                <node id="n1" stepCode="START" stepType="START" x="40" y="200"/>
                <node id="n2" stepCode="OPE010" stepName="氧化" stepType="PROCESS" x="180" y="200"/>
                <node id="n3" stepCode="QCP010" stepName="膜厚检" stepType="INSPECT" x="340" y="200"/>
                <node id="n4" stepCode="GATE" stepName="分档" stepType="DECISION" x="500" y="200"/>
                <node id="n5" stepCode="END" stepType="END" x="680" y="120"/>
                <node id="n6" stepCode="OPE020" stepName="返工" stepType="REWORK" x="500" y="330"/>
              </nodes>
              <edges>
                <edge id="e1" from="n1" to="n2" edgeType="MAIN"/>
                <edge id="e2" from="n2" to="n3" edgeType="MAIN"/>
                <edge id="e3" from="n3" to="n4" edgeType="MAIN"/>
                <edge id="e4" from="n4" to="n5" edgeType="PASS" condition="bin == 'B1'"/>
                <edge id="e5" from="n4" to="n6" edgeType="FAIL" condition="bin == 'B9'"/>
                <edge id="e6" from="n6" to="n3" edgeType="REWORK"/>
              </edges>
              <qtime from="n2" to="n4" maxMinutes="120" action="HOLD"/>
            </canvas>
            """;

    private String createFlow() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("flowName", "规则测试流程", "flowCode", "FRL01"))))
                .andExpect(status().isOk())
                .andReturn();
        flowId = om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
        mvc.perform(put("/api/v1/mds/flows/" + flowId + "/canvas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("xml", XML))))
                .andExpect(status().isOk());
        return flowId;
    }

    private String bodyOf(String url) throws Exception {
        MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String postRule(String payload, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/flow-rules?flowId=" + flowId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String rulePayload(String code, String effect, String condition,
                               String nodeId, String edgeType, String effectParam) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("ruleCode", code);
        m.put("ruleName", "规则 " + code);
        m.put("joinPoint", "BEFORE_DISPATCH");
        m.put("effect", effect);
        m.put("conditionExpr", condition);
        m.put("nodeId", nodeId);
        m.put("edgeType", edgeType);
        m.put("effectParam", effectParam);
        m.put("priority", 100);
        m.put("enabled", true);
        return om.writeValueAsString(m);
    }

    private String evaluate(String joinPoint, String nodeId, String varsJson) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/flow-rules/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "flowId", flowId,
                                "joinPoint", joinPoint,
                                "nodeId", nodeId,
                                "vars", om.readValue(varsJson, Map.class)))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- cases

    @Test
    @Order(1)
    void setupFlowWithCanvas() throws Exception {
        createFlow();
        assertThat(bodyOf("/api/v1/mds/flows/" + flowId)).contains("\"stepCount\":6");
    }

    @Test
    @Order(2)
    void createAndListRules() throws Exception {
        // 手工 HOLD 规则（节点锚点 n2，条件 elapsedMinutes > 100）
        postRule(rulePayload("R001", "HOLD", "elapsedMinutes > 100", "n2", null, "氧化后等待超限"), 200);
        // 重复编码 → 409
        assertThat(postRule(rulePayload("R001", "HOLD", null, null, null, null), 409))
                .contains("\"code\":2003");
        // 条件语法错误 → 400
        assertThat(postRule(rulePayload("R002", "HOLD", "bin === 'B1'", null, null, null), 400))
                .contains("\"code\":2000");
        // ROUTE_OVERRIDE 缺目标 → 400；目标节点不存在 → 404
        assertThat(postRule(rulePayload("R003", "ROUTE_OVERRIDE", null, null, null, null), 400))
                .contains("\"code\":2000");
        assertThat(postRule(rulePayload("R004", "ROUTE_OVERRIDE", null, null, null, "n99"), 404))
                .contains("\"code\":2001");
        // 步骤/节点锚点不存在 → 404
        assertThat(postRule(rulePayload("R005", "HOLD", null, "n99", null, null), 404))
                .contains("\"code\":2001");
        // 清单按 priority 升序
        String list = bodyOf("/api/v1/mds/flow-rules?flowId=" + flowId);
        assertThat(list).contains("R001");
    }

    @Test
    @Order(3)
    void evaluateHoldShortCircuit() throws Exception {
        // 超限 → HOLD 终态
        String over = evaluate("BEFORE_DISPATCH", "n2", "{\"elapsedMinutes\": 999}");
        assertThat(over).contains("\"action\":\"HOLD\"").contains("R001");
        // 未超限 → 无命中 → 隐式放行
        String within = evaluate("BEFORE_DISPATCH", "n2", "{\"elapsedMinutes\": 10}");
        assertThat(within).contains("\"action\":\"ALLOW\"");
        // 锚点不匹配（规则挂 n2，评估 n3）→ 放行
        assertThat(evaluate("BEFORE_DISPATCH", "n3", "{\"elapsedMinutes\": 999}"))
                .contains("\"action\":\"ALLOW\"");
        // 未知变量 → 等值比较恒 false → 放行
        assertThat(evaluate("BEFORE_DISPATCH", "n2", "{}")).contains("\"action\":\"ALLOW\"");
    }

    @Test
    @Order(4)
    void applyQtimeTemplateAndEvaluate() throws Exception {
        mvc.perform(post("/api/v1/mds/flow-rules/apply-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("flowId", flowId, "templateKey", "QTIME_OVER_HOLD"))))
                .andExpect(status().isOk());
        // 模板规则：BEFORE_DISPATCH @ n2，elapsedMinutes > 120 → HOLD
        String over = evaluate("BEFORE_DISPATCH", "n2", "{\"elapsedMinutes\": 500}");
        assertThat(over).contains("TPL-QH-1").contains("\"action\":\"HOLD\"");
    }

    @Test
    @Order(5)
    void applyBinRouteTemplateAndEvaluate() throws Exception {
        mvc.perform(post("/api/v1/mds/flow-rules/apply-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("flowId", flowId, "templateKey", "BIN_ROUTE"))))
                .andExpect(status().isOk());
        // 命中 bin == 'B1'（n4 出边 PASS）→ ROUTE 决策，参数 = 目标节点 n5
        String hit = evaluate("AFTER_STEP_COMPLETE", "n4", "{\"bin\": \"B1\"}");
        assertThat(hit).contains("\"action\":\"ROUTE\"").contains("n5");
        // bin = B9 命中 FAIL 边模板（→ n6 改派）；B5 两边都不命中 → 隐式放行
        assertThat(evaluate("AFTER_STEP_COMPLETE", "n4", "{\"bin\": \"B9\"}"))
                .contains("\"action\":\"ROUTE\"").contains("n6");
        assertThat(evaluate("AFTER_STEP_COMPLETE", "n4", "{\"bin\": \"B5\"}"))
                .contains("\"action\":\"ALLOW\"");
    }

    @Test
    @Order(6)
    void frozenVersionIsReadonlyForRules() throws Exception {
        mvc.perform(post("/api/v1/mds/flows/" + flowId + "/freeze")).andExpect(status().isOk());
        // 冻结后维护规则 → 400（OPERATION_NOT_ALLOWED）
        assertThat(postRule(rulePayload("R010", "HOLD", null, null, null, null), 400))
                .contains("\"code\":2004");
        // 冻结版本 evaluate 只读消费照常可用
        assertThat(evaluate("BEFORE_DISPATCH", "n2", "{\"elapsedMinutes\": 500}"))
                .contains("\"action\":\"HOLD\"");
    }

    @Test
    @Order(7)
    void updateAndDeleteRule() throws Exception {
        // 解冻退回编辑态
        mvc.perform(post("/api/v1/mds/flows/" + flowId + "/unfreeze")).andExpect(status().isOk());
        String list = bodyOf("/api/v1/mds/flow-rules?flowId=" + flowId);
        // 按 ruleCode 精确找 id（清单按 priority 排序，第一条不一定是 R001）
        String ruleId = null;
        String qhId = null;
        for (var node : om.readTree(list).path("data")) {
            String code = node.path("ruleCode").asText();
            if ("R001".equals(code)) {
                ruleId = node.path("id").asText();
            }
            if ("TPL-QH-1".equals(code)) {
                qhId = node.path("id").asText();
            }
        }

        // 停用 R001 → evaluate 不再命中（HOLD 终态消失）
        Map<String, Object> payload = new HashMap<>();
        payload.put("ruleCode", "R001");
        payload.put("joinPoint", "BEFORE_DISPATCH");
        payload.put("effect", "HOLD");
        payload.put("conditionExpr", "elapsedMinutes > 100");
        payload.put("nodeId", "n2");
        payload.put("enabled", false);
        payload.put("priority", 100);
        mvc.perform(put("/api/v1/mds/flow-rules/" + ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(payload)))
                .andExpect(status().isOk());
        // 删掉同挂接点的模板规则后，超限评估只剩被停用的 R001 → 隐式放行
        mvc.perform(delete("/api/v1/mds/flow-rules/" + qhId)).andExpect(status().isOk());
        assertThat(evaluate("BEFORE_DISPATCH", "n2", "{\"elapsedMinutes\": 999}"))
                .contains("\"action\":\"ALLOW\"");

        // 删除 R001 → 清单不再含
        mvc.perform(delete("/api/v1/mds/flow-rules/" + ruleId)).andExpect(status().isOk());
        assertThat(bodyOf("/api/v1/mds/flow-rules?flowId=" + flowId)).doesNotContain("R001");
    }
}
