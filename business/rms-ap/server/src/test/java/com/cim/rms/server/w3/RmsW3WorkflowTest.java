package com.cim.rms.server.w3;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W3 功能集成测试：签核流（提交/通过/拒绝）+ 版本比对 + 操作审计 三域贯通。
 *
 * <p>覆盖 Req.md FR-L4（签核驱动状态机）、FR-C1/C3（版本↔版本比对）、FR-H1/H3（审计留痕）。
 * 响应体含中文时统一用 {@code getContentAsString(StandardCharsets.UTF_8)} 取（MockMvc 缺省 ISO-8859-1）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-w3-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmsW3WorkflowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private static String recipeId;
    private static String v1Id;
    private static String v2Id;

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    @Test
    @Order(1)
    void setupRecipeWithV1() throws Exception {
        // 建机型
        MvcResult t = mvc.perform(post("/api/v1/rms/device-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "ETCH-300", "name", "刻蚀 300"))))
                .andExpect(status().isOk())
                .andReturn();
        String typeId = om.readTree(t.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        // 建配方（带初始 Body → v1）
        MvcResult r = mvc.perform(post("/api/v1/rms/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "RCP-001",
                                "name", "刻蚀主配方",
                                "deviceTypeId", typeId,
                                "bodyFormat", "TEXT",
                                "bodyBase64", b64("HELLO-BODY-V1")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("RCP-001"))
                .andReturn();
        recipeId = om.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();

        // v1 存在且为 DRAFT
        MvcResult vs = mvc.perform(get("/api/v1/rms/recipes/{id}/versions", recipeId))
                .andExpect(status().isOk())
                .andReturn();
        var versions = om.readTree(vs.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(versions.size()).isEqualTo(1);
        assertThat(versions.get(0).path("status").asText()).isEqualTo("DRAFT");
        v1Id = versions.get(0).path("id").asText();
    }

    @Test
    @Order(2)
    void submitSignoff_putsVersionPendingSign() throws Exception {
        MvcResult s = mvc.perform(post("/api/v1/rms/signoffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("recipeId", recipeId, "versionId", v1Id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String sid = om.readTree(s.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("id").asText();
        assertThat(sid).isNotBlank();

        assertThat(versionStatus(v1Id)).isEqualTo("PENDING_SIGN");
    }

    @Test
    @Order(3)
    void approveSignoff_activatesVersion() throws Exception {
        String sid = pendingSignoffId();
        mvc.perform(post("/api/v1/rms/signoffs/{id}/approve", sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(versionStatus(v1Id)).isEqualTo("ACTIVE");
        // 主档 activeVersionId 指向 v1
        MvcResult d = mvc.perform(get("/api/v1/rms/recipes/{id}", recipeId)).andExpect(status().isOk()).andReturn();
        String activeId = om.readTree(d.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("recipe").path("activeVersionId").asText();
        assertThat(activeId).isEqualTo(v1Id);
    }

    @Test
    @Order(4)
    void newVersion_rejectSignoff_backToDraft() throws Exception {
        // 新版本 v2
        MvcResult nv = mvc.perform(post("/api/v1/rms/recipes/{id}/versions", recipeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bodyFormat", "TEXT", "bodyBase64", b64("HELLO-BODY-V2"),
                                "changeSummary", "改参数"))))
                .andExpect(status().isOk())
                .andReturn();
        v2Id = om.readTree(nv.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data").path("id").asText();

        // 发起 + 拒绝
        mvc.perform(post("/api/v1/rms/signoffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("recipeId", recipeId, "versionId", v2Id))))
                .andExpect(status().isOk());
        String sid = pendingSignoffId();
        mvc.perform(post("/api/v1/rms/signoffs/{id}/reject", sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "参数不符"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(versionStatus(v2Id)).isEqualTo("DRAFT");
        // v1 仍为 ACTIVE（拒绝不影响已生效版本）
        assertThat(versionStatus(v1Id)).isEqualTo("ACTIVE");
    }

    @Test
    @Order(5)
    void compareVersions_findsBodyHashDiff() throws Exception {
        MvcResult c = mvc.perform(get("/api/v1/rms/compares/versions")
                        .param("leftVersionId", v1Id)
                        .param("rightVersionId", v2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identical").value(false))
                .andReturn();
        var items = om.readTree(c.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data").path("items");
        boolean hashChanged = false;
        boolean statusChanged = false;
        for (var it : items) {
            if ("bodyHash".equals(it.path("field").asText()) && it.path("changed").asBoolean()) hashChanged = true;
            if ("status".equals(it.path("field").asText()) && it.path("changed").asBoolean()) statusChanged = true;
        }
        assertThat(hashChanged).as("bodyHash 应不同").isTrue();
        assertThat(statusChanged).as("status 应不同（ACTIVE vs DRAFT）").isTrue();
    }

    @Test
    @Order(6)
    void auditLog_recordsAllKeyActions() throws Exception {
        MvcResult a = mvc.perform(get("/api/v1/rms/audit").param("limit", "200"))
                .andExpect(status().isOk())
                .andReturn();
        var rows = om.readTree(a.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        var types = new java.util.HashSet<String>();
        for (var row : rows) {
            types.add(row.path("type").asText());
        }
        assertThat(types).contains("RECIPE_CREATED", "RECIPE_VERSION_CREATED",
                "SIGNOFF_SUBMITTED", "SIGNOFF_APPROVED", "SIGNOFF_REJECTED");
        // 按类型过滤有效
        mvc.perform(get("/api/v1/rms/audit").param("type", "SIGNOFF_APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("SIGNOFF_APPROVED"));
    }

    @Test
    @Order(7)
    void submitWithReason_carriesParamDiffAndTimeline() throws Exception {
        // v2 重新发起（带原因 + 签核路由）
        MvcResult s = mvc.perform(post("/api/v1/rms/signoffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recipeId", recipeId, "versionId", v2Id,
                                "reason", "工艺窗口调整：刻蚀时间 +5%",
                                "signRoute", "COUNTERSIGN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reason").value("工艺窗口调整：刻蚀时间 +5%"))
                .andExpect(jsonPath("$.data.signRoute").value("COUNTERSIGN"))
                .andReturn();
        var detail = om.readTree(s.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");

        // 参数级差异附件：相对上一生效版 v1，bodyHash 必变
        var diff = detail.path("paramDiff");
        assertThat(diff.size()).as("签核单应携带参数差异附件").isGreaterThan(0);
        boolean hashDiff = false;
        for (var it : diff) {
            if ("bodyHash".equals(it.path("field").asText()) && it.path("changed").asBoolean()) hashDiff = true;
        }
        assertThat(hashDiff).as("差异附件应含 bodyHash 变更").isTrue();

        // 事件时间线：SUBMITTED + PARAM_DIFF
        var evTypes = new java.util.HashSet<String>();
        for (var e : detail.path("timeline")) {
            evTypes.add(e.path("eventType").asText());
        }
        assertThat(evTypes).contains("SUBMITTED", "PARAM_DIFF");
    }

    @Test
    @Order(8)
    void tempAllow_presetAndCancel() throws Exception {
        String sid = pendingSignoffId();
        String from = java.time.Instant.now().toString();
        String to = java.time.Instant.now().plusSeconds(3600).toString();

        // 无原因应被拒（强制留痕）
        mvc.perform(post("/api/v1/rms/signoffs/{id}/temp-allow", sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("from", from, "to", to))))
                .andExpect(status().is4xxClientError());

        // 正常预设 → ACTIVE
        mvc.perform(post("/api/v1/rms/signoffs/{id}/temp-allow", sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("from", from, "to", to,
                                "scope", "光刻区 · 3 台", "reason", "急单放行"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tempAllowStatus").value("ACTIVE"));

        // KPI 汇总应反映限时放行中
        MvcResult sum = mvc.perform(get("/api/v1/rms/signoffs/summary"))
                .andExpect(status().isOk()).andReturn();
        int tempActive = om.readTree(sum.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("tempActive").asInt();
        assertThat(tempActive).isGreaterThanOrEqualTo(1);

        // 取消 → NONE
        mvc.perform(post("/api/v1/rms/signoffs/{id}/temp-cancel", sid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "撤销"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tempAllowStatus").value("NONE"));
    }

    @Test
    @Order(9)
    void batchDecision_approvesWithPerItemResult() throws Exception {
        String sid = pendingSignoffId();
        MvcResult b = mvc.perform(post("/api/v1/rms/signoffs/batch-decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("ids", java.util.List.of(sid),
                                "decision", "APPROVE", "comment", "批量通过（含一条无效）"))))
                .andExpect(status().isOk())
                .andReturn();
        var results = om.readTree(b.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).path("ok").asBoolean()).isTrue();

        // v2 生效，v1 转 OBSOLETE（单生效约束）
        assertThat(versionStatus(v2Id)).isEqualTo("ACTIVE");
        assertThat(versionStatus(v1Id)).isEqualTo("OBSOLETE");
    }

    @Test
    @Order(10)
    void compareLogs_twoLevelDrilldown() throws Exception {
        // 运行一次比对并落日志
        MvcResult run = mvc.perform(post("/api/v1/rms/compares/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("baselineVersionId", v1Id, "targetVersionId", v2Id,
                                "mode", "FULL_BODY"))))
                .andExpect(status().isOk())
                .andReturn();
        String logId = om.readTree(run.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("logId").asText();
        assertThat(logId).isNotBlank();

        // 日志列表 + 汇总
        mvc.perform(get("/api/v1/rms/compares/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(logId));
        MvcResult sum = mvc.perform(get("/api/v1/rms/compares/logs/summary"))
                .andExpect(status().isOk()).andReturn();
        assertThat(om.readTree(sum.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("total").asInt()).isGreaterThanOrEqualTo(1);

        // 两级下钻：明细含差异项
        MvcResult d = mvc.perform(get("/api/v1/rms/compares/logs/{id}", logId))
                .andExpect(status().isOk()).andReturn();
        var items = om.readTree(d.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("items");
        assertThat(items.size()).isGreaterThan(0);
    }

    @Test
    @Order(11)
    void auditSummaryAndRecipeHistory() throws Exception {
        MvcResult sum = mvc.perform(get("/api/v1/rms/audit/summary"))
                .andExpect(status().isOk()).andReturn();
        var s = om.readTree(sum.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(s.path("total").asInt()).isGreaterThan(0);
        assertThat(s.path("success").asInt()).isGreaterThan(0);

        // 配方对象历史：按编码追溯
        MvcResult h = mvc.perform(get("/api/v1/rms/audit/recipe-history").param("code", "RCP-001"))
                .andExpect(status().isOk()).andReturn();
        assertThat(om.readTree(h.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").size()).isGreaterThan(0);
    }

    // ---------- helpers ----------

    private String versionStatus(String versionId) throws Exception {
        MvcResult vs = mvc.perform(get("/api/v1/rms/recipes/{id}/versions", recipeId))
                .andExpect(status().isOk()).andReturn();
        for (var v : om.readTree(vs.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data")) {
            if (versionId.equals(v.path("id").asText())) {
                return v.path("status").asText();
            }
        }
        return null;
    }

    /** 取当前 PENDING 签核单 id（列表过滤 PENDING 取第一条）。 */
    private String pendingSignoffId() throws Exception {
        MvcResult s = mvc.perform(get("/api/v1/rms/signoffs").param("status", "PENDING"))
                .andExpect(status().isOk()).andReturn();
        var rows = om.readTree(s.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        assertThat(rows.size()).isGreaterThan(0);
        return rows.get(0).path("id").asText();
    }
}
