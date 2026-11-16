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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 制造流程（Flow）集成测试：XML 画布为源 + 解析产物表 + 图校验守门 + 版本生命周期。
 *
 * <p>覆盖：FLOW 编码规则占号、画布 XML 保存守门、解析产物按 XML 原顺序、
 * 版本生命周期（UNFROZEN → FROZEN → ACTIVE → DISABLED；冻结/激活校验前置；
 * 单 Active 约束：激活新版时同族旧 ACTIVE 自动降级 FROZEN）、SUBFLOW/bin/
 * 批处理/Hold（V13）、检验配置/跨站 Q-time/SPLIT-MERGE（V14）、复制（同族新版本）、
 * 删除（UNFROZEN/DISABLED 可删）。</p>
 *
 * <p>认证关闭直通；独立 H2 库名（各测类隔离）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-flow-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String flowId;
    private static String flowCode;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- helpers

    /** 建 FLOW 启用规则（FL + 两位流水）。 */
    private void createNamingRule() throws Exception {
        mvc.perform(post("/api/v1/mds/naming-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of(
                                "objectType", "FLOW",
                                "ruleName", "流程编码",
                                "segments", java.util.List.of(
                                        Map.of("type", "LITERAL", "text", "FL", "length", 2),
                                        Map.of("type", "SEQ", "length", 2, "start", 1, "scope", "GLOBAL")),
                                "enabled", true))))
                .andExpect(status().isOk());
    }

    /** 新建流程（编码留空 = 自动占号）。 */
    private String createFlow(String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("flowName", name))))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
    }

    private String getJson(String url) throws Exception {
        MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 合法画布：START→OPE010→QCP010→GATE→(PASS)END / (FAIL)OPE020→(REWORK)QCP010。 */
    private static final String GOOD_XML = """
            <canvas version="1">
              <nodes>
                <node id="n1" stepCode="START" stepType="START" x="40" y="200"/>
                <node id="n2" stepCode="OPE010" stepName="氧化" stepType="PROCESS" x="180" y="200"/>
                <node id="n3" stepCode="QCP010" stepName="膜厚检" stepType="INSPECT" x="340" y="200"/>
                <node id="n4" stepCode="GATE" stepName="合格判定" stepType="DECISION" x="500" y="200"/>
                <node id="n5" stepCode="END" stepType="END" x="680" y="120"/>
                <node id="n6" stepCode="OPE020" stepName="返工重氧化" stepType="REWORK" x="500" y="330"/>
              </nodes>
              <edges>
                <edge id="e1" from="n1" to="n2" edgeType="MAIN"/>
                <edge id="e2" from="n2" to="n3" edgeType="MAIN"/>
                <edge id="e3" from="n3" to="n4" edgeType="MAIN"/>
                <edge id="e4" from="n4" to="n5" edgeType="PASS"/>
                <edge id="e5" from="n4" to="n6" edgeType="FAIL"/>
                <edge id="e6" from="n6" to="n3" edgeType="REWORK"/>
              </edges>
            </canvas>
            """;

    /** 保存画布，返回响应体（业务码在 JSON 里：2000/2001/2003 按守门类型）。 */
    private String saveCanvas(String id, String xml) throws Exception {
        MvcResult result = mvc.perform(put("/api/v1/mds/flows/" + id + "/canvas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("xml", xml))))
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 保存画布（期望 200），返回 detail JSON。 */
    private String saveCanvasOk(String id, String xml) throws Exception {
        String body = saveCanvas(id, xml);
        assertThat(body).as("saveCanvas ok: %s", body).contains("\"code\":0");
        return body;
    }

    /** 冻结 + 激活（图校验在 freeze/activate 内做前置）。 */
    private void freezeAndActivate(String id) throws Exception {
        mvc.perform(post("/api/v1/mds/flows/" + id + "/freeze")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/mds/flows/" + id + "/activate")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- cases

    @Test
    @Order(1)
    void createFlowWithNamingRule() throws Exception {
        createNamingRule();
        MvcResult result = mvc.perform(post("/api/v1/mds/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("flowName", "CIS 前段标准流程"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flowCode").value("FL01"))
                .andExpect(jsonPath("$.data.status").value("UNFROZEN"))
                // 新建 = 自成版本族 V1
                .andExpect(jsonPath("$.data.familyCode").value("FL01"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andReturn();
        flowId = om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
        flowCode = "FL01";

        // 手填不匹配规则正则 → 400
        mvc.perform(post("/api/v1/mds/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("flowName", "x", "flowCode", "BAD"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(2)
    void saveCanvasAndParseStepsInOrder() throws Exception {
        var tree = om.readTree(saveCanvasOk(flowId, GOOD_XML)).path("data");
        // 解析产物按 XML 原顺序 step_no 1..6，node_id 透传
        assertThat(tree.path("steps").size()).isEqualTo(6);
        assertThat(tree.path("steps").get(0).path("stepCode").asText()).isEqualTo("START");
        assertThat(tree.path("steps").get(0).path("stepNo").asInt()).isEqualTo(1);
        assertThat(tree.path("steps").get(3).path("stepCode").asText()).isEqualTo("GATE");
        assertThat(tree.path("steps").get(3).path("nodeId").asText()).isEqualTo("n4");
        assertThat(tree.path("steps").get(5).path("stepNo").asInt()).isEqualTo(6);
        assertThat(tree.path("edges").size()).isEqualTo(6);
        assertThat(tree.path("edges").get(5).path("edgeType").asText()).isEqualTo("REWORK");
        // XML 原样入库（坐标只进 XML 不进解析产物表）
        assertThat(tree.path("flow").path("flowXml").asText()).contains("x=\"180\"");
    }

    @Test
    @Order(3)
    void canvasSaveGuards() throws Exception {
        // XML 语法错 → 400
        assertThat(saveCanvas(flowId, "<canvas><oops")).contains("\"code\":2000");
        // 未知节点类型 → 400
        assertThat(saveCanvas(flowId, GOOD_XML
                .replace("stepType=\"PROCESS\"", "stepType=\"MAGIC\""))).contains("\"code\":2000");
        // stepCode 重复 → 400
        assertThat(saveCanvas(flowId, GOOD_XML
                .replace("stepCode=\"OPE020\"", "stepCode=\"OPE010\""))).contains("\"code\":2003");
        // 边端点缺失 → 400
        assertThat(saveCanvas(flowId, GOOD_XML
                .replace("from=\"n4\" to=\"n6\"", "from=\"n4\" to=\"n9\""))).contains("\"code\":2001");
        // DECISION 出边用 MAIN → 400
        assertThat(saveCanvas(flowId, GOOD_XML
                .replace("from=\"n4\" to=\"n5\" edgeType=\"PASS\"", "from=\"n4\" to=\"n5\" edgeType=\"MAIN\"")))
                .contains("\"code\":2000");
        // equipType 引用真实守门 → 400/2001
        assertThat(saveCanvas(flowId, GOOD_XML
                .replace("stepType=\"PROCESS\"", "stepType=\"PROCESS\" equipType=\"NOPE\"")))
                .contains("\"code\":2001");
    }

    @Test
    @Order(4)
    void lifecycleGuards() throws Exception {
        // 场景 A：删掉 START → 校验拒绝冻结
        String noStart = GOOD_XML
                .replace("<node id=\"n1\" stepCode=\"START\" stepType=\"START\" x=\"40\" y=\"200\"/>", "")
                .replace("<edge id=\"e1\" from=\"n1\" to=\"n2\" edgeType=\"MAIN\"/>", "");
        saveCanvas(flowId, noStart);
        mvc.perform(post("/api/v1/mds/flows/" + flowId + "/freeze"))
                .andExpect(status().isBadRequest());
        MvcResult vr = mvc.perform(post("/api/v1/mds/flows/" + flowId + "/validate"))
                .andExpect(status().isOk()).andReturn();
        String problems = om.readTree(vr.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("problems").toString();
        assertThat(problems).contains("START");

        // 场景 B：主线不贯通（START 只连到孤立节点）→ 拒绝
        String broken = GOOD_XML.replace("from=\"n2\" to=\"n3\"", "from=\"n2\" to=\"n1\"");
        // n2→n1 指回 START，主线到不了 END
        saveCanvas(flowId, broken);
        mvc.perform(post("/api/v1/mds/flows/" + flowId + "/freeze"))
                .andExpect(status().isBadRequest());

        // 场景 C：REWORK 死循环（返工指向下游）→ 拒绝
        String loop = GOOD_XML.replace("from=\"n6\" to=\"n3\"", "from=\"n6\" to=\"n5\"");
        saveCanvas(flowId, loop);
        mvc.perform(post("/api/v1/mds/flows/" + flowId + "/freeze"))
                .andExpect(status().isBadRequest());

        // 恢复合法画布 → 校验通过 → 冻结 → 激活
        saveCanvasOk(flowId, GOOD_XML);
        mvc.perform(post("/api/v1/mds/flows/" + flowId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
        mvc.perform(post("/api/v1/mds/flows/" + flowId + "/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"));
        mvc.perform(post("/api/v1/mds/flows/" + flowId + "/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // ACTIVE 只读：改画布 → 400（OPERATION_NOT_ALLOWED=2004）
        mvc.perform(put("/api/v1/mds/flows/" + flowId + "/canvas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("xml", GOOD_XML))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2004));
        // 冻结中的版本不可解冻回 UNFROZEN 之外的路径：ACTIVE 不能 freeze（状态机守门）
        mvc.perform(post("/api/v1/mds/flows/" + flowId + "/freeze"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void copyCreatesFamilyVersion() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/flows/" + flowId + "/copy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNFROZEN"))
                .andExpect(jsonPath("$.data.flowCode").value("FL02"))
                .andExpect(jsonPath("$.data.familyCode").value("FL01"))
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andReturn();
        String copyId = om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
        var tree = om.readTree(getJson("/api/v1/mds/flows/" + copyId)).path("data");
        assertThat(tree.path("steps").size()).isEqualTo(6);
        assertThat(tree.path("edges").size()).isEqualTo(6);
        assertThat(tree.path("flow").path("flowXml").asText()).contains("OPE010");
        assertThat(tree.path("flow").path("flowName").asText()).contains("V2");

        // 同族新版本是 UNFROZEN 可删
        mvc.perform(delete("/api/v1/mds/flows/" + copyId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mds/flows/" + copyId)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- V13 第一批：SUBFLOW / bin / 批处理 / Hold

    /** 简单主线画布（START → SUBFLOW → END；SUBFLOW 节点为 SUB_CODE，引用码由调用方替换）。 */
    private static final String SUBFLOW_XML = """
            <canvas version="1">
              <nodes>
                <node id="s1" stepCode="START" stepType="START" x="40" y="200"/>
                <node id="s2" stepCode="SUB_CODE" stepName="嵌套子流程" stepType="SUBFLOW" flowRef="%s" x="180" y="200"/>
                <node id="s3" stepCode="END" stepType="END" x="340" y="200"/>
              </nodes>
              <edges>
                <edge id="se1" from="s1" to="s2" edgeType="MAIN"/>
                <edge id="se2" from="s2" to="s3" edgeType="MAIN"/>
              </edges>
            </canvas>
            """;

    /** 普通 UNFROZEN 流程（自动占号），返回 [id, code]。 */
    private String[] createFlowAuto() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/mds/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("flowName", "测试流程"))))
                .andExpect(status().isOk())
                .andReturn();
        var tree = om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        return new String[] { tree.path("id").asText(), tree.path("flowCode").asText() };
    }

    @Test
    @Order(6)
    void subflowGuardsAndLifecycle() throws Exception {
        // 未指定 flowRef → 400
        String a = createFlowAuto()[0];
        assertThat(saveCanvas(a, SUBFLOW_XML.replace("flowRef=\"%s\"", ""))).contains("\"code\":2000");
        // 引用不存在的流程 → 2001
        assertThat(saveCanvas(a, SUBFLOW_XML.formatted("NOPE"))).contains("\"code\":2001");

        // 引用环：A→B、B→A → validate 报环路
        String[] aPair = createFlowAuto();
        String[] bPair = createFlowAuto();
        saveCanvasOk(aPair[0], SUBFLOW_XML.formatted(bPair[1]));
        saveCanvasOk(bPair[0], SUBFLOW_XML.formatted(aPair[1]));
        String problems = om.readTree(mvc.perform(post("/api/v1/mds/flows/" + aPair[0] + "/validate"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).path("data").path("problems").toString();
        assertThat(problems).contains("环路");

        // 正向：先激活普通流程 D，再让 E 引用 D → 校验通过可激活
        String[] d = createFlowAuto();
        saveCanvasOk(d[0], GOOD_XML);
        freezeAndActivate(d[0]);
        String[] e = createFlowAuto();
        var tree = om.readTree(saveCanvasOk(e[0], SUBFLOW_XML.formatted(d[1]))).path("data");
        assertThat(tree.path("steps").get(1).path("flowRef").asText()).isEqualTo(d[1]);
        mvc.perform(post("/api/v1/mds/flows/" + e[0] + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true));
        freezeAndActivate(e[0]);

        // 引用未激活流程 → 冻结守门拒绝（g 引用未激活的 f）
        String[] f = createFlowAuto();
        saveCanvasOk(f[0], GOOD_XML);
        String[] g = createFlowAuto();
        saveCanvasOk(g[0], SUBFLOW_XML.formatted(f[1]));
        mvc.perform(post("/api/v1/mds/flows/" + g[0] + "/freeze"))
                .andExpect(status().isBadRequest());
    }

    /** 决策 bin 词表画布（GATE 带 GOOD/NG 两档，出边条件引用 bin）。 */
    private static final String BIN_XML = """
            <canvas version="1">
              <nodes>
                <node id="b1" stepCode="START" stepType="START" x="40" y="200"/>
                <node id="b2" stepCode="OPE010" stepName="氧化" stepType="PROCESS" batchMode="BATCH" batchSize="12" holdPoint="true" holdReason="关键站后释放" x="180" y="200">
                  <bin code="IGNORED" name="非决策" type="PASS"/>
                </node>
                <node id="b3" stepCode="GATE" stepName="分档" stepType="DECISION" x="340" y="200">
                  <bin code="GOOD" name="良品" type="PASS"/>
                  <bin code="NG" name="不良" type="FAIL"/>
                </node>
                <node id="b4" stepCode="END" stepType="END" x="500" y="120"/>
                <node id="b5" stepCode="SCRAP" stepName="报废判定" stepType="END" x="500" y="280"/>
              </nodes>
              <edges>
                <edge id="be1" from="b1" to="b2" edgeType="MAIN"/>
                <edge id="be2" from="b2" to="b3" edgeType="MAIN"/>
                <edge id="be3" from="b3" to="b4" edgeType="PASS" condition="GOOD"/>
                <edge id="be4" from="b3" to="b5" edgeType="FAIL" condition="NG"/>
              </edges>
            </canvas>
            """;

    @Test
    @Order(7)
    void binsBatchAndHoldGuards() throws Exception {
        String id = createFlowAuto()[0];
        // bin 词表合法 + 条件命中 → 保存成功，解析产物带 batchMode/holdPoint/bin
        var tree = om.readTree(saveCanvasOk(id, BIN_XML)).path("data");
        assertThat(tree.path("steps").get(1).path("batchMode").asText()).isEqualTo("BATCH");
        assertThat(tree.path("steps").get(1).path("batchSize").asInt()).isEqualTo(12);
        assertThat(tree.path("steps").get(1).path("holdPoint").asBoolean()).isTrue();
        assertThat(tree.path("steps").get(2).path("stepCode").asText()).isEqualTo("GATE");

        // 条件不在 bin 词表 → 400
        assertThat(saveCanvas(id, BIN_XML.replace("condition=\"GOOD\"", "condition=\"BADBIN\"")))
                .contains("\"code\":2000");
        // bin 类型非法 → 400
        assertThat(saveCanvas(id, BIN_XML.replace(
                "<bin code=\"NG\" name=\"不良\" type=\"FAIL\"/>",
                "<bin code=\"NG\" name=\"不良\" type=\"WHAT\"/>"))).contains("\"code\":2000");
        // bin 编码重复 → 2003
        assertThat(saveCanvas(id, BIN_XML.replace(
                "<bin code=\"NG\" name=\"不良\" type=\"FAIL\"/>",
                "<bin code=\"GOOD\" name=\"不良\" type=\"FAIL\"/>"))).contains("\"code\":2003");
        // 非 DECISION 节点设 flowRef → 400
        assertThat(saveCanvas(id, GOOD_XML.replace(
                "stepCode=\"OPE010\" stepName=\"氧化\" stepType=\"PROCESS\"",
                "stepCode=\"OPE010\" stepName=\"氧化\" stepType=\"PROCESS\" flowRef=\"FL01\"")))
                .contains("\"code\":2000");
        // batchMode 非法 → 400
        assertThat(saveCanvas(id, BIN_XML.replace("batchMode=\"BATCH\"", "batchMode=\"WHAT\"")))
                .contains("\"code\":2000");

        // 激活 → 复制 → bin 随 XML 克隆
        freezeAndActivate(id);
        MvcResult result = mvc.perform(post("/api/v1/mds/flows/" + id + "/copy"))
                .andExpect(status().isOk()).andReturn();
        String copyId = om.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
        String copyXml = om.readTree(getJson("/api/v1/mds/flows/" + copyId))
                .path("data").path("flow").path("flowXml").asText();
        assertThat(copyXml).contains("<bin code=\"GOOD\"");
        assertThat(om.readTree(getJson("/api/v1/mds/flows/" + copyId))
                .path("data").path("steps").get(2).path("stepType").asText()).isEqualTo("DECISION");
        // 清理副本
        mvc.perform(delete("/api/v1/mds/flows/" + copyId)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- V14 第二批：检验配置 / 跨站 Q-time / SPLIT-MERGE

    /** SPLIT/MERGE 主线画布 + 跨站 Q-time + INSPECT 检验配置（由调用方替换变体）。 */
    private static final String SM_XML = """
            <canvas version="1">
              <nodes>
                <node id="m1" stepCode="START" stepType="START" x="40" y="200"/>
                <node id="m2" stepCode="SPL010" stepName="分批" stepType="SPLIT" x="180" y="200"/>
                <node id="m3" stepCode="QCP010" stepName="膜厚检" stepType="INSPECT" inspectPlan="EDC-01" samplingRate="25" x="340" y="120"/>
                <node id="m4" stepCode="QCP020" stepName="外观检" stepType="INSPECT" x="340" y="280"/>
                <node id="m5" stepCode="MRG010" stepName="合批" stepType="MERGE" x="500" y="200"/>
                <node id="m6" stepCode="END" stepType="END" x="660" y="200"/>
              </nodes>
              <edges>
                <edge id="me1" from="m1" to="m2" edgeType="MAIN"/>
                <edge id="me2" from="m2" to="m3" edgeType="MAIN"/>
                <edge id="me3" from="m2" to="m4" edgeType="MAIN"/>
                <edge id="me4" from="m3" to="m5" edgeType="MAIN"/>
                <edge id="me5" from="m4" to="m5" edgeType="MAIN"/>
                <edge id="me6" from="m5" to="m6" edgeType="MAIN"/>
              </edges>
              <qtime from="m1" to="m6" maxMinutes="120" action="HOLD"/>
            </canvas>
            """;

    @Test
    @Order(8)
    void inspectQtimeSplitMergeGuards() throws Exception {
        String id = createFlowAuto()[0];
        // 正向：检验配置 + 跨站 Q-time + SPLIT/MERGE 保存成功，解析产物齐
        var tree = om.readTree(saveCanvasOk(id, SM_XML)).path("data");
        assertThat(tree.path("steps").get(2).path("inspectPlan").asText()).isEqualTo("EDC-01");
        assertThat(tree.path("steps").get(2).path("samplingRate").asDouble()).isEqualTo(25.0);
        assertThat(tree.path("qtimes").size()).isEqualTo(1);
        assertThat(tree.path("qtimes").get(0).path("maxMinutes").asInt()).isEqualTo(120);
        assertThat(tree.path("qtimes").get(0).path("action").asText()).isEqualTo("HOLD");

        // 抽样率越界 (>100) → 400
        assertThat(saveCanvas(id, SM_XML.replace("samplingRate=\"25\"", "samplingRate=\"150\"")))
                .contains("\"code\":2000");
        // 抽样率非正 (0) → 400
        assertThat(saveCanvas(id, SM_XML.replace("samplingRate=\"25\"", "samplingRate=\"0\"")))
                .contains("\"code\":2000");
        // 非 INSPECT 节点设检验计划 → 400
        assertThat(saveCanvas(id, GOOD_XML.replace(
                "stepCode=\"OPE010\" stepName=\"氧化\" stepType=\"PROCESS\"",
                "stepCode=\"OPE010\" stepName=\"氧化\" stepType=\"PROCESS\" inspectPlan=\"EDC-01\"")))
                .contains("\"code\":2000");
        // qtime 引用不存在节点 → 2001
        assertThat(saveCanvas(id, SM_XML.replace("to=\"m6\" maxMinutes", "to=\"n9\" maxMinutes")))
                .contains("\"code\":2001");
        // qtime from==to → 2000
        assertThat(saveCanvas(id, SM_XML.replace("from=\"m1\" to=\"m6\" maxMinutes", "from=\"m6\" to=\"m6\" maxMinutes")))
                .contains("\"code\":2000");
        // qtime action 非法 → 2000
        assertThat(saveCanvas(id, SM_XML.replace("action=\"HOLD\"", "action=\"WHAT\"")))
                .contains("\"code\":2000");
        // qtime maxMinutes 非正 → 2000
        assertThat(saveCanvas(id, SM_XML.replace("maxMinutes=\"120\"", "maxMinutes=\"0\"")))
                .contains("\"code\":2000");
        // qtime 同对站重复 → 2003
        assertThat(saveCanvas(id, SM_XML.replace("</edges>", "</edges><qtime from=\"m1\" to=\"m6\" maxMinutes=\"60\" action=\"SCRAP\"/>")))
                .contains("\"code\":2003");
        // SPLIT 出边用 PASS → 400
        assertThat(saveCanvas(id, SM_XML.replace("from=\"m2\" to=\"m3\" edgeType=\"MAIN\"", "from=\"m2\" to=\"m3\" edgeType=\"PASS\"")))
                .contains("\"code\":2000");

        // SPLIT 出边不足 2 条 → 冻结守门拒绝
        saveCanvasOk(id, SM_XML
                .replace("<edge id=\"me3\" from=\"m2\" to=\"m4\" edgeType=\"MAIN\"/>", "")
                .replace("<node id=\"m4\" stepCode=\"QCP020\" stepName=\"外观检\" stepType=\"INSPECT\" x=\"340\" y=\"280\"/>", "")
                .replace("<edge id=\"me5\" from=\"m4\" to=\"m5\" edgeType=\"MAIN\"/>", ""));
        MvcResult vr1 = mvc.perform(post("/api/v1/mds/flows/" + id + "/validate"))
                .andExpect(status().isOk()).andReturn();
        assertThat(om.readTree(vr1.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("problems").toString()).contains("分批节点");

        // MERGE 入边不足 2 条 → 冻结守门拒绝
        saveCanvasOk(id, SM_XML.replace("<edge id=\"me5\" from=\"m4\" to=\"m5\" edgeType=\"MAIN\"/>", ""));
        MvcResult vr2 = mvc.perform(post("/api/v1/mds/flows/" + id + "/validate"))
                .andExpect(status().isOk()).andReturn();
        assertThat(om.readTree(vr2.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("problems").toString()).contains("合批节点");

        // 跨站 Q-time 起点走不到终点（约束悬空）→ 冻结守门拒绝
        saveCanvasOk(id, SM_XML.replace("from=\"m1\" to=\"m6\" maxMinutes", "from=\"m6\" to=\"m1\" maxMinutes"));
        MvcResult vr3 = mvc.perform(post("/api/v1/mds/flows/" + id + "/validate"))
                .andExpect(status().isOk()).andReturn();
        assertThat(om.readTree(vr3.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("problems").toString()).contains("走不到");

        // 恢复合法画布 → 激活；单 Active 约束：同族旧 ACTIVE 自动降级 FROZEN
        saveCanvasOk(id, SM_XML);
        freezeAndActivate(id);
        MvcResult copyResult = mvc.perform(post("/api/v1/mds/flows/" + id + "/copy"))
                .andExpect(status().isOk()).andReturn();
        String copyId = om.readTree(copyResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
        // qtime 随复制克隆
        assertThat(om.readTree(getJson("/api/v1/mds/flows/" + copyId)).path("data").path("qtimes").size())
                .isEqualTo(1);
        freezeAndActivate(copyId);
        String oldStatus = om.readTree(getJson("/api/v1/mds/flows/" + id)).path("data").path("flow").path("status").asText();
        assertThat(oldStatus).as("旧 ACTIVE 应降级 FROZEN").isEqualTo("FROZEN");

        // DISABLED 可删除（淘汰预备删除）
        mvc.perform(post("/api/v1/mds/flows/" + copyId + "/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        mvc.perform(delete("/api/v1/mds/flows/" + copyId)).andExpect(status().isOk());
    }
}
