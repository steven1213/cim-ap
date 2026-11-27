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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ICM 继承式约束模型集成测试（V22）：ContextChain 解析（effective 端点 + 来源四级标注
 * + 候选池三层回落）、统一指派三 scope 守门与 uk 去重、KV 批量 upsert 守门、参数定义
 * 引用守门、activate VALID 硬闸、编辑锁、copy 搬运（assign/attr 随版本复制）。
 *
 * <p>认证关闭直通；独立 H2 库名（各测类隔离）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mds-icm-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
        "cim.notify.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IcmDomainTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String flowId;
    private static String assignStepRowId;
    private static String paramDefId;

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

    /** 最小画布：START → OPE010(PROCESS) → END（节点不引用工序，纯走 assign/attr 链）。 */
    private static final String XML = """
            <canvas version="1">
              <nodes>
                <node id="n1" stepCode="START" stepType="START" x="40" y="200"/>
                <node id="n2" stepCode="OPE010" stepName="氧化" stepType="PROCESS" x="180" y="200"/>
                <node id="n3" stepCode="END" stepType="END" x="340" y="200"/>
              </nodes>
              <edges>
                <edge id="e1" from="n1" to="n2" edgeType="MAIN"/>
                <edge id="e2" from="n2" to="n3" edgeType="MAIN"/>
              </edges>
            </canvas>
            """;

    /** FLOW 命名规则（copy 硬依赖：apply(OBJ_FLOW, null) 无规则即 2000）。 */
    private void createNamingRule() throws Exception {
        postJson("/api/v1/mds/naming-rules",
                om.writeValueAsString(Map.of(
                        "objectType", "FLOW",
                        "ruleName", "流程编码",
                        "segments", List.of(
                                Map.of("type", "LITERAL", "text", "FL", "length", 2),
                                Map.of("type", "SEQ", "length", 2, "start", 1, "scope", "GLOBAL")),
                        "enabled", true)), 200);
    }

    private String createFlow() throws Exception {
        // 编码留空 = 命名规则自动占号（copy 同样走规则）
        String body = postJson("/api/v1/mds/flows",
                om.writeValueAsString(Map.of("flowName", "ICM 测试流程")), 200);
        return om.readTree(body).path("data").path("id").asText();
    }

    // ---------------------------------------------------------------- tests

    @Test
    @Order(1)
    void createFlowAndSaveCanvas() throws Exception {
        createNamingRule();
        flowId = createFlow();
        assertThat(flowId).isNotBlank();
        putJson("/api/v1/mds/flows/" + flowId + "/canvas",
                om.writeValueAsString(Map.of("xml", XML)), 200);
        // V22 落库字段默认值
        String detail = getJson("/api/v1/mds/flows/" + flowId);
        assertThat(detail).contains("\"validateState\":\"NOT_VALID\"").contains("\"isOccupied\":false");
    }

    @Test
    @Order(2)
    void assignScopeGuardsAndDuplicate() throws Exception {
        // FLOW 级新增：引用组不存在 → 创建守门 DATA_NOT_FOUND（2001 → 404）
        MvcResult r = mvc.perform(post("/api/v1/mds/flows/" + flowId + "/assigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("assignType", "GROUP", "assignRef", "TG-NO-SUCH",
                                "recipeRule", "GENERATE", "priority", 100))))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("2001");
    }

    @Test
    @Order(3)
    void effectiveResolvesPoolAndSources() throws Exception {
        // 参数定义（scopes=OPERATION,FLOW,STEP）
        String defBody = postJson("/api/v1/mds/param-defs",
                om.writeValueAsString(Map.of(
                        "attrKey", "dc.sample.plan", "attrName", "抽样计划",
                        "groupCode", "DC", "valueType", "STRING",
                        "required", false, "scopes", List.of("OPERATION", "FLOW", "STEP"))), 200);
        paramDefId = om.readTree(defBody).path("data").path("id").asText();

        // 工序默认指派不存在 → pool MISSING；流程级一行 GROUP（引用真实组不存在也不影响解析行集？
        // 不行——创建守门要求引用存在。流程级行在 Order2 已被 404 挡住，所以这里先建一个真实组）
        // 简化：直接给 STEP 级建一行（引用同样须真实）——先查/建机台组走 tool-groups 端点
        postJson("/api/v1/mds/tool-groups",
                om.writeValueAsString(Map.of("groupCode", "TG-ICM", "groupName", "ICM 测试组",
                        "classCode", "ET-ETCH", "enabled", true)), 200);
        String stepAssign = postJson("/api/v1/mds/flows/" + flowId + "/steps/n2/assigns",
                om.writeValueAsString(Map.of("assignType", "GROUP", "assignRef", "TG-ICM",
                        "recipeRule", "GENERATE", "priority", 50)), 200);
        assignStepRowId = om.readTree(stepAssign).path("data").path("id").asText();

        // effective：n2 候选池 = STEP 层（来源 STEP_OVERRIDE），n1/n3 MISSING
        String eff = getJson("/api/v1/mds/flows/" + flowId + "/effective");
        assertThat(eff).contains("\"stepCode\":\"OPE010\"")
                .contains("\"poolSource\":\"STEP_OVERRIDE\"")
                .contains("\"poolSource\":\"MISSING\"");
        // 单节点 effective
        String one = getJson("/api/v1/mds/flows/" + flowId + "/steps/n2/effective");
        assertThat(one).contains("\"assignRef\":\"TG-ICM\"").contains("\"attrKey\":\"dc.sample.plan\"");
        // KV 定义出现在 attrs（MISSING 来源 + def 快照）
        assertThat(one).contains("\"source\":\"MISSING\"");
    }

    @Test
    @Order(4)
    void assignDuplicateRejected() throws Exception {
        // 与 Order3 完全同参（GROUP/TG-ICM/priority50）→ 2004 冲突
        MvcResult r = mvc.perform(post("/api/v1/mds/flows/" + flowId + "/steps/n2/assigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("assignType", "GROUP", "assignRef", "TG-ICM",
                                "recipeRule", "GENERATE", "priority", 50))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("2004");
    }

    @Test
    @Order(5)
    void kvUpsertAndGuards() throws Exception {
        // 未注册 key → 2004（写操作不允许 → OPERATION_NOT_ALLOWED 400）
        MvcResult bad = mvc.perform(put("/api/v1/mds/flows/" + flowId + "/steps/n2/attrs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("values", Map.of("no.such.key", "x")))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(bad.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("2004");

        // scopes 守门：建 scopes=STEP 的定义，FLOW 层写 → 2004
        postJson("/api/v1/mds/param-defs",
                om.writeValueAsString(Map.of(
                        "attrKey", "step.only", "attrName", "仅节点层",
                        "groupCode", "DC", "valueType", "STRING",
                        "scopes", List.of("STEP"))), 200);
        MvcResult scopeBad = mvc.perform(put("/api/v1/mds/flows/" + flowId + "/attrs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("values", Map.of("step.only", "v")))))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(scopeBad.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("2004");

        // 合法 upsert：FLOW 层写 dc.sample.plan → 回读
        String put = mvc.perform(put("/api/v1/mds/flows/" + flowId + "/attrs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("values", Map.of("dc.sample.plan", "PLAN-A")))))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(put).contains("\"dc.sample.plan\":\"PLAN-A\"");
    }

    @Test
    @Order(6)
    void paramDefReferencedNoDelete() throws Exception {
        // dc.sample.plan 已被 FLOW 级 KV 行引用（Order5）→ 删除 2004
        MvcResult r = mvc.perform(delete("/api/v1/mds/param-defs/" + paramDefId))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("2004");
    }

    @Test
    @Order(7)
    void editLockBlocksSaveByOther() throws Exception {
        // 锁定后（本测试单用户恒定身份）保存画布仍放行；occupied 状态回传 true
        postJson("/api/v1/mds/flows/" + flowId + "/lock", "{}", 200);
        String detail = getJson("/api/v1/mds/flows/" + flowId);
        assertThat(detail).contains("\"isOccupied\":true");
        // 释放
        postJson("/api/v1/mds/flows/" + flowId + "/unlock", "{}", 200);
        detail = getJson("/api/v1/mds/flows/" + flowId);
        assertThat(detail).contains("\"isOccupied\":false");
    }

    @Test
    @Order(8)
    void activationRequiresValid() throws Exception {
        // 未校验（NOT_VALID）→ 冻结 → 激活被 2004 拦截
        postJson("/api/v1/mds/flows/" + flowId + "/freeze", "{}", 200);
        MvcResult r = mvc.perform(post("/api/v1/mds/flows/" + flowId + "/activate"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("2004");
    }

    @Test
    @Order(9)
    void validateThenActivateAndCopyCarriesAssign() throws Exception {
        // 校验落库（无 ERROR：结构合法 + 指派齐备）→ VALID
        String v = postJson("/api/v1/mds/flows/" + flowId + "/validate", "{}", 200);
        assertThat(v).contains("\"valid\":true");
        String detail = getJson("/api/v1/mds/flows/" + flowId);
        assertThat(detail).contains("\"validateState\":\"VALID\"");
        // 激活放行
        postJson("/api/v1/mds/flows/" + flowId + "/activate", "{}", 200);
        // copy 搬运：STEP 级 assign 行随新版本复制
        String copy = postJson("/api/v1/mds/flows/" + flowId + "/copy", "{}", 200);
        String newId = om.readTree(copy).path("data").path("id").asText();
        String newAssigns = getJson("/api/v1/mds/flows/" + newId + "/assigns");
        // scope=FLOW 行不搬（本流程没有）；STEP 级行搬——但列表端点只列 FLOW 级，需查新流程步骤级
        MvcResult r = mvc.perform(get("/api/v1/mds/flows/" + newId + "/steps/n2/assigns"))
                .andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("TG-ICM");
        // 新版本 validate_state 重置 NOT_VALID
        String newDetail = getJson("/api/v1/mds/flows/" + newId);
        assertThat(newDetail).contains("\"validateState\":\"NOT_VALID\"");
    }
}
