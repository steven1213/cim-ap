package com.cim.rms.server.w5;

import com.cim.rms.server.device.DeviceAdminService;
import com.cim.rms.server.integration.DownloadStage;
import com.cim.rms.server.integration.RecipeDownloadJobRepository;
import com.cim.rms.server.integration.RmsAlarmRepository;
import com.cim.rms.server.integration.RmsHoldRepository;
import com.cim.rms.server.qualification.QualificationService;
import com.cim.rms.server.recipe.BodyFormat;
import com.cim.rms.server.recipe.RecipeService;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
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
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W5 集成层测试（Req §5.2 IF-COM 幂等、§5.3 IF-E1/IF-E2/IF-E3）。
 *
 * <p>覆盖三件事：</p>
 * <ol>
 *   <li><b>幂等去重</b>：同 correlationId 重发上传不产生第二个版本；同内容重传亦被去重；</li>
 *   <li><b>闸门拒绝不缓存</b>：未登记资格的下发被判 REJECTED，补齐资格后同一 correlationId
 *       重试即可放行（不落「陈旧拒绝」陷阱）；</li>
 *   <li><b>IF-E2 阶段机</b>：非法迁移（跳阶）被拒；回读 HASH 不一致判 FAILED 并报 RMS_DRIFT；
 *       失败可重新入队。</li>
 * </ol>
 *
 * <p>响应体中文断言一律 {@code getContentAsString(StandardCharsets.UTF_8)}（MockMvc 缺省 ISO-8859-1）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-w5-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmsIntegrationIdempotencyTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    @Autowired
    DeviceAdminService deviceAdmin;
    @Autowired
    RecipeService recipeService;
    @Autowired
    QualificationService qualificationService;
    @Autowired
    RecipeVersionRepository versionRepository;
    @Autowired
    RecipeDownloadJobRepository jobRepository;
    @Autowired
    RmsAlarmRepository alarmRepository;
    @Autowired
    RmsHoldRepository holdRepository;
    @Autowired
    com.cim.rms.server.integration.RecipeDownloadService downloadService;

    private static String typeId;
    private static String deviceId;
    private static String recipeId;      // 已登记资格，用于上传/阶段机
    private static String v1Id;
    private static String gateRecipeId;  // 未登记资格，用于闸门测试
    private static String gateV1Id;

    private static final String BODY_V1 = b64("HELLO-BODY-V1");

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String base64) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(Base64.getDecoder().decode(base64));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    private long versionCount(String rid) {
        return versionRepository.findByRecipeIdAndDeletedFalseOrderByVersionNoDesc(rid).size();
    }

    // ==================== setup ====================

    @Test
    @Order(1)
    void setup() throws Exception {
        typeId = deviceAdmin.createType("IDEM-300", "幂等测试机型", null, null, null).id();
        deviceId = deviceAdmin.createDevice("IDEM-T01", "幂等测试机台", typeId, null, null, null).id();

        recipeId = recipeService.create("IDEM-R1", "幂等测试配方", typeId, null, null, false, null,
                "TEXT", BODY_V1, sha256(BODY_V1)).id();
        v1Id = versionRepository.findByRecipeIdAndDeletedFalseOrderByVersionNoDesc(recipeId)
                .get(0).getId();
        recipeService.activate(recipeId, v1Id);
        qualificationService.grant(recipeId, "LATEST_ACTIVE", null, deviceId, null, null,
                "资质测试", Instant.now().minusSeconds(60), null);

        gateRecipeId = recipeService.create("IDEM-R2", "未登记资格配方", typeId, null, null, false, null,
                "TEXT", BODY_V1, sha256(BODY_V1)).id();
        gateV1Id = versionRepository.findByRecipeIdAndDeletedFalseOrderByVersionNoDesc(gateRecipeId)
                .get(0).getId();
        recipeService.activate(gateRecipeId, gateV1Id);

        assertThat(versionCount(recipeId)).isEqualTo(1);
    }

    // ==================== IF-E1 上传幂等 ====================

    @Test
    @Order(2)
    void upload_sameCorrelationId_returnsSameResultWithoutNewVersion() throws Exception {
        long before = versionCount(recipeId);
        Map<String, Object> req = Map.of(
                "toolId", deviceId, "ppid", "IDEM-PPID-01", "recipeUid", recipeId,
                "bodyFormat", "TEXT", "bodyBase64", b64("UPLOAD-A"), "bodyHash", sha256(b64("UPLOAD-A")),
                "uploadReason", "MANUAL", "opId", "eap", "correlationId", "CORR-UP-1");

        MvcResult first = mvc.perform(post("/api/v1/rms/integration/upload")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ACCEPTED"))
                .andReturn();
        int v1 = om.readTree(first.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("version").asInt();
        assertThat(versionCount(recipeId)).isEqualTo(before + 1);

        // 重发同一 correlationId（模拟 EAP 超时重试）
        MvcResult again = mvc.perform(post("/api/v1/rms/integration/upload")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ACCEPTED"))
                .andReturn();
        int v2 = om.readTree(again.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("version").asInt();

        assertThat(v2).as("重试必须回放首次版本号").isEqualTo(v1);
        assertThat(versionCount(recipeId)).as("重试不得新增版本").isEqualTo(before + 1);
    }

    @Test
    @Order(3)
    void upload_sameContentWithoutCorrelationId_dedupesByContentHash() throws Exception {
        long before = versionCount(recipeId);
        Map<String, Object> req = Map.of(
                "toolId", deviceId, "ppid", "IDEM-PPID-02", "recipeUid", recipeId,
                "bodyFormat", "TEXT", "bodyBase64", b64("UPLOAD-B"), "bodyHash", sha256(b64("UPLOAD-B")),
                "uploadReason", "DRIFT_AUDIT", "opId", "eap");
        mvc.perform(post("/api/v1/rms/integration/upload")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/rms/integration/upload")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk());
        assertThat(versionCount(recipeId)).as("同内容重传应按内容哈希去重").isEqualTo(before + 1);
    }

    // ==================== IF-E2 闸门拒绝不缓存 ====================

    @Test
    @Order(4)
    void download_gateRejectionIsNotCached_andRetrySucceedsAfterQualification() throws Exception {
        Map<String, Object> req = Map.of(
                "toolId", deviceId, "ppidOnTool", "IDEM-GATE-PPID", "recipeUid", gateRecipeId,
                "downloadMode", "IMMEDIATE", "requestor", "eap", "correlationId", "CORR-DL-GATE");

        MvcResult r1 = mvc.perform(post("/api/v1/rms/integration/download")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("REJECTED"))
                .andReturn();
        String stage1 = om.readTree(r1.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("stage").asText();
        assertThat(stage1).isEqualTo(DownloadStage.FAILED.name());
        assertThat(jobRepository.findByDeviceIdAndDeletedFalseOrderByCreateTimeDesc(deviceId))
                .as("闸门拒绝不应产生下发任务")
                .noneMatch(j -> "CORR-DL-GATE".equals(j.getCorrelationId()));

        // 运维补齐资格
        qualificationService.grant(gateRecipeId, "LATEST_ACTIVE", null, deviceId, null, null,
                "补登资质", Instant.now().minusSeconds(60), null);

        // 同一 correlationId 重试 → 应被重新评估并放行（证明闸门拒绝未被缓存）
        mvc.perform(post("/api/v1/rms/integration/download")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.stage").value(DownloadStage.QUEUED.name()));

        assertThat(jobRepository.findByDeviceIdAndDeletedFalseOrderByCreateTimeDesc(deviceId))
                .filteredOn(j -> "CORR-DL-GATE".equals(j.getCorrelationId()))
                .as("重试成功后只应有一个下发任务")
                .hasSize(1);
    }

    // ==================== IF-E2 阶段机 ====================

    @Test
    @Order(5)
    void download_stageMachine_rejectsIllegalJump() throws Exception {
        mvc.perform(post("/api/v1/rms/integration/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toolId", deviceId, "ppidOnTool", "IDEM-P1",
                                "recipeUid", recipeId, "downloadMode", "IMMEDIATE",
                                "requestor", "eap", "correlationId", "CORR-DL-SM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("QUEUED"));

        // 跳阶：QUEUED 直接到 TOOL_ACKED → 400 / code 2000
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-SM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stage", "TOOL_ACKED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));

        // 合法链路推进到 VERIFIED（回读 HASH 一致）
        String good = sha256(BODY_V1);
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-SM")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("stage", "GRANTED"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stage").value("GRANTED"));
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-SM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stage", "SENT_TO_TOOL", "toolAck", "ACK"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stage").value("SENT_TO_TOOL"));
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-SM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stage", "TOOL_ACKED", "toolAck", "ACK"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stage").value("TOOL_ACKED"));
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-SM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stage", "VERIFIED", "readbackHash", good))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("VERIFIED"))
                .andExpect(jsonPath("$.data.verified").value(true));

        // VERIFIED 为终态，不可再迁
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-SM")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("stage", "GRANTED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void download_readbackHashMismatch_failsAndRaisesDriftAlarm() throws Exception {
        long alarmsBefore = alarmRepository.findByDeletedFalseOrderByCreateTimeDesc().stream()
                .filter(a -> "RMS_DRIFT".equals(a.getAlarmCode())).count();

        mvc.perform(post("/api/v1/rms/integration/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toolId", deviceId, "ppidOnTool", "IDEM-P2",
                                "recipeUid", recipeId, "downloadMode", "ON_IDLE",
                                "requestor", "eap", "correlationId", "CORR-DL-DRIFT"))))
                .andExpect(status().isOk());
        for (String s : new String[]{"GRANTED", "SENT_TO_TOOL", "TOOL_ACKED"}) {
            mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-DRIFT")
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("stage", s))))
                    .andExpect(status().isOk());
        }
        // 回读不一致 → VERIFIED 请求应被判定为 FAILED
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-DRIFT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stage", "VERIFIED", "readbackHash", sha256(b64("TAMPERED"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("FAILED"))
                .andExpect(jsonPath("$.data.verified").value(false));

        long alarmsAfter = alarmRepository.findByDeletedFalseOrderByCreateTimeDesc().stream()
                .filter(a -> "RMS_DRIFT".equals(a.getAlarmCode())).count();
        assertThat(alarmsAfter).as("回读不符应报 RMS_DRIFT 报警").isEqualTo(alarmsBefore + 1);

        // 失败后可重新入队（Req 21「失败重试队列」）
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-DRIFT")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("stage", "QUEUED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("QUEUED"));
    }

    @Test
    @Order(7)
    void download_failRequiresReason() throws Exception {
        mvc.perform(post("/api/v1/rms/integration/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("toolId", deviceId, "ppidOnTool", "IDEM-P3",
                                "recipeUid", recipeId, "downloadMode", "BATCH", "batchId", "B-1",
                                "requestor", "eap", "correlationId", "CORR-DL-BATCH"))))
                .andExpect(status().isOk());
        // 失败不留痕 → 400
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-BATCH")
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("stage", "FAILED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(2000));
        // 带原因 → 成功
        mvc.perform(post("/api/v1/rms/integration/downloads/{c}/ack", "CORR-DL-BATCH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("stage", "FAILED", "errorDesc", "机台忙碌超时"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stage").value("FAILED"));

        // 批量进度聚合
        mvc.perform(get("/api/v1/rms/integration/downloads/batch-progress").param("batchId", "B-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.failed").value(1));
    }

    // ==================== IF-E3 在线比对幂等 ====================

    @Test
    @Order(8)
    void onlineCompare_sameCorrelationId_doesNotDuplicateHoldOrAlarm() throws Exception {
        // 机台 Body 与生效版不一致 → 比对失败 → 挂 Hold + 报警
        Map<String, Object> req = Map.of(
                "toolId", deviceId, "lotId", "LOT-1", "ppidOnTool", "IDEM-PPID-01",
                "recipeUid", recipeId, "bodyFormat", "TEXT",
                "bodyBase64", b64("DRIFTED"), "bodyHash", sha256(b64("DRIFTED")),
                "cmpMode", "FULL_BODY", "baselineType", "ACTIVE", "correlationId", "CORR-CMP-1");

        mvc.perform(post("/api/v1/rms/integration/online-compare")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cmpResult").value("FAIL"))
                .andExpect(jsonPath("$.data.holdApplied").value(true))
                .andExpect(jsonPath("$.data.alarmRaised").value(true));

        long holdsAfterFirst = holdRepository.findByDeviceIdAndActiveTrueAndDeletedFalse(deviceId).size();
        long alarmsAfterFirst = alarmRepository.findByDeletedFalseOrderByCreateTimeDesc().stream()
                .filter(a -> "RMS_COMPARE_FAIL".equals(a.getAlarmCode())).count();
        assertThat(holdsAfterFirst).isGreaterThan(0);
        assertThat(alarmsAfterFirst).isGreaterThan(0);

        // 同 correlationId 重发 → 回放，不应新增 Hold / 报警
        mvc.perform(post("/api/v1/rms/integration/online-compare")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cmpResult").value("FAIL"));

        assertThat(holdRepository.findByDeviceIdAndActiveTrueAndDeletedFalse(deviceId))
                .as("重试不得重复挂 Hold").hasSize((int) holdsAfterFirst);
        assertThat(alarmRepository.findByDeletedFalseOrderByCreateTimeDesc().stream()
                .filter(a -> "RMS_COMPARE_FAIL".equals(a.getAlarmCode())).count())
                .as("重试不得重复报警").isEqualTo(alarmsAfterFirst);
    }

    // ==================== 幂等登记可查 ====================

    @Test
    @Order(9)
    void idempotencyRegistry_isQueryable() throws Exception {
        mvc.perform(get("/api/v1/rms/integration/idempotency").param("scope", "IF_E1_UPLOAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[*].scope").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("IF_E1_UPLOAD"))));

        MvcResult dl = mvc.perform(get("/api/v1/rms/integration/idempotency")
                        .param("scope", "IF_E2_DOWNLOAD"))
                .andExpect(status().isOk())
                .andReturn();
        var keys = om.readTree(dl.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data");
        // 受理成功的下发请求应留下幂等登记（闸门拒绝的不留）
        assertThat(keys.findValuesAsText("idemKey")).contains("CORR-DL-GATE");
    }

    // ==================== 服务层断言（中文消息只能在服务层断言） ====================

    @Test
    @Order(10)
    void stageTransition_unknownJob_throwsNotFound() {
        assertThatThrownBy(() -> downloadService.advance("CORR-NOT-EXIST", DownloadStage.GRANTED,
                null, null, null))
                .hasMessageContaining("下发任务不存在");
    }
}
