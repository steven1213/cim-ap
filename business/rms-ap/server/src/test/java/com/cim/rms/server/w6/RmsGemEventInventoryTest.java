package com.cim.rms.server.w6;

import com.cim.rms.server.device.DeviceAdminService;
import com.cim.rms.server.integration.DownloadStage;
import com.cim.rms.server.integration.EquipmentEventService;
import com.cim.rms.server.integration.RecipeDownloadJob;
import com.cim.rms.server.integration.RecipeDownloadJobRepository;
import com.cim.rms.server.integration.RecipeDownloadService;
import com.cim.rms.server.integration.RmsAlarm;
import com.cim.rms.server.integration.RmsAlarmRepository;
import com.cim.rms.server.integration.RmsEquipmentEventRepository;
import com.cim.rms.server.integration.ToolRecipeInventoryRepository;
import com.cim.rms.server.integration.ToolRecipeMapService;
import com.cim.rms.server.qualification.QualificationService;
import com.cim.rms.server.recipe.RecipeService;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W6 设备侧配方治理测试（SEMI E30 GEM 事件模型 + S7F19 机台目录核对）。
 *
 * <p>覆盖四组行为：</p>
 * <ol>
 *   <li><b>PP_CHANGE 漂移处置</b>：已纳管配方的 EDITED/DELETED → 报 RMS_DRIFT + 挂 Hold；
 *       同 eventId 重放不重复处置（幂等）；</li>
 *   <li><b>PP_CHANGE 孤儿信号</b>：未纳管 PPID → 报 RMS_ORPHAN_PPID，但不误判为漂移；</li>
 *   <li><b>PP_VERIFICATION_FAILED 联动</b>：机台校验失败 → 报 RMS_VERIF_FAIL，并把在途下发任务判 FAILED
 *       （避免任务永悬 TOOL_ACKED）；</li>
 *   <li><b>机台配方清单核对</b>：OK / DRIFT / ORPHAN 三类判定 + MISSING（RMS 有映射、机台未上报）。</li>
 * </ol>
 *
 * <p>响应体中文断言一律 {@code getContentAsString(StandardCharsets.UTF_8)}（MockMvc 缺省 ISO-8859-1）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-w6-gem;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmsGemEventInventoryTest {

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
    ToolRecipeMapService ppidMapService;
    @Autowired
    RecipeVersionRepository versionRepository;
    @Autowired
    RecipeDownloadJobRepository jobRepository;
    @Autowired
    RecipeDownloadService downloadService;
    @Autowired
    RmsAlarmRepository alarmRepository;
    @Autowired
    RmsEquipmentEventRepository eventRepository;
    @Autowired
    ToolRecipeInventoryRepository inventoryRepository;

    private static String typeId;
    private static String deviceId;
    private static String recipeId;
    private static String activeHash;

    private static final String BODY = b64("GEM-BODY-V1");
    private static final String PPID_OK = "GEM-PPID-01";
    private static final String PPID_DRIFT = "GEM-PPID-DRIFT";
    private static final String PPID_MISSING = "GEM-PPID-MISSING";
    private static final String PPID_ORPHAN = "GEM-ORPHAN-01";

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String base64) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(base64));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    private long alarmCount(String code) {
        return alarmRepository.findByDeletedFalseOrderByCreateTimeDesc().stream()
                .filter(a -> code.equals(a.getAlarmCode())).count();
    }

    private List<RmsAlarm> alarms(String code) {
        List<RmsAlarm> out = new ArrayList<>();
        for (RmsAlarm a : alarmRepository.findByDeletedFalseOrderByCreateTimeDesc()) {
            if (code.equals(a.getAlarmCode())) {
                out.add(a);
            }
        }
        return out;
    }

    // ==================== setup ====================

    @Test
    @Order(1)
    void setup() throws Exception {
        typeId = deviceAdmin.createType("GEM-300", "GEM 测试机型", null, null, null).id();
        deviceId = deviceAdmin.createDevice("GEM-T01", "GEM 测试机台", typeId, null, null, null).id();

        recipeId = recipeService.create("GEM-R1", "GEM 测试配方", typeId, null, null, false, null,
                "TEXT", BODY, sha256(BODY)).id();
        String v1 = versionRepository.findByRecipeIdAndDeletedFalseOrderByVersionNoDesc(recipeId)
                .get(0).getId();
        recipeService.activate(recipeId, v1);
        activeHash = sha256(BODY);
        qualificationService.grant(recipeId, "LATEST_ACTIVE", null, deviceId, null, null,
                "资质测试", Instant.now().minusSeconds(60), null);

        // D7 映射：机台 PPID → RMS 配方
        ppidMapService.register(deviceId, null, PPID_OK, recipeId);
        ppidMapService.register(deviceId, null, PPID_DRIFT, recipeId);
        ppidMapService.register(deviceId, null, PPID_MISSING, recipeId);

        assertThat(activeHash).isNotBlank();
    }

    // ==================== PP_CHANGE 漂移处置 ====================

    @Test
    @Order(2)
    void ppChange_editedManagedPpid_raisesDriftAndHolds() throws Exception {
        long driftBefore = alarmCount("RMS_DRIFT");
        Map<String, Object> req = Map.of(
                "eventId", "EVT-EDIT-1", "toolId", deviceId, "eventType", "PP_CHANGE",
                "ppid", PPID_OK, "changeType", "EDITED", "opId", "operatorA",
                "correlationId", "CORR-EVT-1");

        mvc.perform(post("/api/v1/rms/integration/equipment-events")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driftDetected").value(true))
                .andExpect(jsonPath("$.data.alarmRaised").value(true))
                .andExpect(jsonPath("$.data.holdApplied").value(true))
                .andExpect(jsonPath("$.data.handlingResult").exists());

        assertThat(alarmCount("RMS_DRIFT")).as("PPChange 编辑已纳管配方应报漂移").isEqualTo(driftBefore + 1);
        assertThat(alarms("RMS_DRIFT"))
                .anySatisfy(a -> assertThat(a.getMessage()).contains(PPID_OK));

        // 同 eventId 重放（模拟 EAP 重连重放）→ 幂等，不得重复报警
        mvc.perform(post("/api/v1/rms/integration/equipment-events")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk());
        assertThat(alarmCount("RMS_DRIFT")).as("重放同一事件不得重复报警").isEqualTo(driftBefore + 1);
        assertThat(eventRepository.findFirstByIdemKeyAndDeletedFalse("EVT:EVT-EDIT-1"))
                .as("事件按 eventId 落一条").isPresent();
    }

    @Test
    @Order(3)
    void ppChange_holdOnDriftFalse_reportsButDoesNotHold() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("eventId", "EVT-NOHOLD-1");
        req.put("toolId", deviceId);
        req.put("eventType", "PP_CHANGE");
        req.put("ppid", PPID_DRIFT);
        req.put("changeType", "DELETED");
        req.put("holdOnDrift", false);

        mvc.perform(post("/api/v1/rms/integration/equipment-events")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.driftDetected").value(true))
                .andExpect(jsonPath("$.data.holdApplied").value(false));
    }

    @Test
    @Order(4)
    void ppChange_unmappedPpid_raisesOrphanSignalNotDrift() throws Exception {
        long orphanBefore = alarmCount("RMS_ORPHAN_PPID");
        long driftBefore = alarmCount("RMS_DRIFT");
        Map<String, Object> req = Map.of(
                "eventId", "EVT-ORPHAN-1", "toolId", deviceId, "eventType", "PP_CHANGE",
                "ppid", PPID_ORPHAN, "changeType", "CREATED");

        mvc.perform(post("/api/v1/rms/integration/equipment-events")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alarmRaised").value(true))
                .andExpect(jsonPath("$.data.driftDetected").value(false));

        assertThat(alarmCount("RMS_ORPHAN_PPID")).isEqualTo(orphanBefore + 1);
        assertThat(alarmCount("RMS_DRIFT")).as("未纳管 PPID 不应被误判为漂移").isEqualTo(driftBefore);
    }

    @Test
    @Order(5)
    void ppChange_createdMappedPpid_doesNotAlarm() throws Exception {
        long driftBefore = alarmCount("RMS_DRIFT");
        Map<String, Object> req = Map.of(
                "eventId", "EVT-CREATE-1", "toolId", deviceId, "eventType", "PP_CHANGE",
                "ppid", PPID_OK, "changeType", "CREATED");

        mvc.perform(post("/api/v1/rms/integration/equipment-events")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alarmRaised").value(false))
                .andExpect(jsonPath("$.data.driftDetected").value(false));
        assertThat(alarmCount("RMS_DRIFT")).isEqualTo(driftBefore);
    }

    // ==================== PP_VERIFICATION_FAILED 联动下发任务 ====================

    @Test
    @Order(6)
    void ppVerificationFailed_failsInFlightDownloadJob() throws Exception {
        String ppid = "GEM-PPID-VERIF";
        String corr = "CORR-DL-VERIF";

        // 1) 发起下发 → 任务落 QUEUED
        Map<String, Object> dl = Map.of(
                "toolId", deviceId, "ppidOnTool", ppid, "recipeUid", recipeId,
                "downloadMode", "IMMEDIATE", "requestor", "eap", "correlationId", corr);
        mvc.perform(post("/api/v1/rms/integration/download")
                        .contentType(MediaType.APPLICATION_JSON).content(json(dl)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ACCEPTED"));

        // 2) 推进到 TOOL_ACKED（机台已确认接收，尚未回读校验）
        downloadService.advance(corr, DownloadStage.GRANTED, null, null, null);
        downloadService.advance(corr, DownloadStage.SENT_TO_TOOL, "ACK", null, null);
        downloadService.advance(corr, DownloadStage.TOOL_ACKED, "ACK", null, null);

        long verifBefore = alarmCount("RMS_VERIF_FAIL");

        // 3) 机台校验失败事件
        Map<String, Object> evt = Map.of(
                "eventId", "EVT-VERIF-1", "toolId", deviceId,
                "eventType", "PP_VERIFICATION_FAILED", "ppid", ppid,
                "errorDesc", "STEP3 温度超出机台允许范围", "correlationId", "CORR-EVT-VERIF");

        mvc.perform(post("/api/v1/rms/integration/equipment-events")
                        .contentType(MediaType.APPLICATION_JSON).content(json(evt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alarmRaised").value(true))
                .andExpect(jsonPath("$.data.handlingResult").value(org.hamcrest.Matchers.containsString("FAILED")));

        assertThat(alarmCount("RMS_VERIF_FAIL")).isEqualTo(verifBefore + 1);

        RecipeDownloadJob job = jobRepository.findFirstByCorrelationIdAndDeletedFalse(corr).orElseThrow();
        assertThat(job.getStage()).as("在途下发任务应被判 FAILED，不能永悬 TOOL_ACKED")
                .isEqualTo(DownloadStage.FAILED);
        assertThat(job.getErrorDesc()).contains("PPVerificationFailed");
        assertThat(job.getErrorDesc()).contains("温度超出机台允许范围");
    }

    // ==================== 机台配方清单核对 ====================

    @Test
    @Order(7)
    void toolInventory_reconcilesOkDriftOrphanAndMissing() throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item(PPID_OK, activeHash));            // 一致
        items.add(item(PPID_DRIFT, "deadbeef" + "0".repeat(56)));  // 漂移
        items.add(item(PPID_ORPHAN, null));              // 孤儿（无映射）
        // PPID_MISSING 已映射但本次未上报 → MISSING

        Map<String, Object> req = Map.of(
                "toolId", deviceId, "correlationId", "CORR-INV-1", "items", items);

        MvcResult res = mvc.perform(post("/api/v1/rms/integration/tool-inventory")
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.ok").value(1))
                .andExpect(jsonPath("$.data.drift").value(1))
                .andExpect(jsonPath("$.data.orphan").value(1))
                .andReturn();

        String body = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains(PPID_MISSING);

        assertThat(inventoryRepository.findByDeviceIdAndDeletedFalseOrderByPpidOnToolAsc(deviceId))
                .hasSize(3);
    }

    @Test
    @Order(8)
    void toolInventory_queriesReturnSummaryAndRows() throws Exception {
        mvc.perform(get("/api/v1/rms/integration/tool-inventory").param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        mvc.perform(get("/api/v1/rms/integration/tool-inventory")
                        .param("deviceId", deviceId).param("result", "ORPHAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ppidOnTool").value(PPID_ORPHAN))
                .andExpect(jsonPath("$.data[0].reconcileResult").value("ORPHAN"));

        mvc.perform(get("/api/v1/rms/integration/tool-inventory/summary").param("deviceId", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.orphan").value(1))
                .andExpect(jsonPath("$.data.drift").value(1));
    }

    @Test
    @Order(9)
    void equipmentEvents_areQueryable() throws Exception {
        mvc.perform(get("/api/v1/rms/integration/equipment-events").param("toolId", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));

        mvc.perform(get("/api/v1/rms/integration/equipment-events")
                        .param("toolId", deviceId).param("eventType", "PP_VERIFICATION_FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].eventType").value("PP_VERIFICATION_FAILED"));
    }

    private static Map<String, Object> item(String ppid, String hash) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ppid", ppid);
        if (hash != null) {
            m.put("bodyHash", hash);
        }
        return m;
    }

    /** 保留：确保事件服务可注入（构造异常会在此暴露）。 */
    @Autowired(required = false)
    EquipmentEventService equipmentEventServiceBean;

    @Test
    @Order(10)
    void wiring_sanity() {
        assertThat(equipmentEventServiceBean).isNotNull();
    }
}
