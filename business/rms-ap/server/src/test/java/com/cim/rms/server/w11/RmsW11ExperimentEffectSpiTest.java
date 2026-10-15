package com.cim.rms.server.w11;

import com.cim.rms.server.device.DeviceAdminService;
import com.cim.rms.server.effect.ChangeEffectService;
import com.cim.rms.server.experiment.ExperimentService;
import com.cim.rms.server.integration.IntegrationService;
import com.cim.rms.server.qualification.QualificationService;
import com.cim.rms.server.recipe.RecipeService;
import com.cim.rms.server.recipe.body.RecipeBodyParseService;
import com.cim.rms.server.signoff.RecipeSignoffService;
import com.cim.rms.server.signoff.SignRoute;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W11 集成测试：实验配方通道（REQUESTED→APPROVED→CLOSED 状态机 + IF-E2 闸门放行 +
 * 窗口判定）、变更效果挂钩（手动激活/签核激活两条路都登记）、解析器 SPI 注册表。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-w11-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmsW11ExperimentEffectSpiTest {

    @Autowired
    DeviceAdminService deviceAdmin;
    @Autowired
    RecipeService recipeService;
    @Autowired
    QualificationService qualificationService;
    @Autowired
    ExperimentService experimentService;
    @Autowired
    IntegrationService integrationService;
    @Autowired
    ChangeEffectService changeEffectService;
    @Autowired
    RecipeSignoffService signoffService;
    @Autowired
    RecipeBodyParseService parseService;

    private static String typeId;
    private static String deviceId;
    private static String recipeId;
    private static String v1;
    private static String v2;
    private static String experimentId;

    static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static String body(String temp) {
        return b64("{\"meta\":{\"process\":\"DEP\"},\"params\":{\"TEMP\":{\"value\":\"" + temp
                + "\"}},\"steps\":[]}");
    }

    @Test
    @Order(1)
    void setup() {
        typeId = deviceAdmin.createType("T-W11", "W11 机型", null, null, null).id();
        deviceId = deviceAdmin.createDevice("W11-D01", "实验机台", typeId, null, null, null).id();
        recipeId = recipeService.create("RCP-W11-DOE", "DOE 实验配方", typeId, null, null, false,
                null, "JSON_STRUCTURED", body("75"), null).id();
        v1 = recipeService.versionsOf(recipeId).get(0).getId();
        v2 = recipeService.newVersion(recipeId,
                com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED, body("82"),
                null, null, "DOE 参数上调").getId();
        qualificationService.grant(recipeId, "LATEST_ACTIVE", null, deviceId, null, null,
                "W11 测试", null, null);
    }

    @Test
    @Order(2)
    void requestedExperimentDoesNotGrant() {
        ExperimentService.ExperimentView e = experimentService.create(recipeId, v2, deviceId,
                "DOE 第一轮", null,
                Instant.now().plus(2, ChronoUnit.HOURS).toString());
        experimentId = e.id();
        assertThat(e.status()).isEqualTo("REQUESTED");
        assertThat(experimentService.activeFor(deviceId, v2)).isFalse();
    }

    @Test
    @Order(3)
    void draftVersionStillRejectedWithoutExperiment() {
        IntegrationService.DownloadResult r = integrationService.downloadRecipe(
                new IntegrationService.DownloadReq(deviceId, null, recipeId, null,
                        "IMMEDIATE", null, null, "w11-tester", null));
        // 缺省 version → activeVersion 为 null → 资格/版本闸门拒绝（实验未批）
        assertThat(r.result()).isEqualTo("REJECTED");
    }

    @Test
    @Order(4)
    void approvedExperimentOpensDownloadGate() {
        experimentService.approve(experimentId, "批准 DOE");
        assertThat(experimentService.activeFor(deviceId, v2)).isTrue();
        IntegrationService.DownloadResult r = integrationService.downloadRecipe(
                new IntegrationService.DownloadReq(deviceId, null, recipeId, 2,
                        "IMMEDIATE", null, null, "w11-tester", null));
        assertThat(r.result()).isEqualTo("ACCEPTED");
        assertThat(r.version()).isEqualTo(2);
    }

    @Test
    @Order(5)
    void closedExperimentClosesGateAgain() {
        experimentService.close(experimentId, "DOE 结束");
        assertThat(experimentService.activeFor(deviceId, v2)).isFalse();
    }

    @Test
    @Order(6)
    void manualActivationRecordsChangeEffect() {
        recipeService.activate(recipeId, v1);
        var rows = changeEffectService.list(recipeId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).toVersionNo()).isEqualTo(1);
        assertThat(rows.get(0).fromVersionNo()).isNull();
    }

    @Test
    @Order(7)
    void signoffApprovalRecordsSecondEffect() {
        // v2 须先回 DRAFT？v2 是 DRAFT（未经签核）→ 直接发起签核并批准
        String signoffId = signoffService.submit(recipeId, v2, "v2 生效", SignRoute.SINGLE).id();
        signoffService.approve(signoffId, "同意");
        var rows = changeEffectService.list(recipeId);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).toVersionNo()).isEqualTo(2); // 时间倒序，v2 在前
        assertThat(rows.get(0).fromVersionNo()).isEqualTo(1);
        assertThat(rows.get(0).paramDiffCount()).isGreaterThanOrEqualTo(1); // TEMP 75→82
    }

    @Test
    @Order(8)
    void parserSpiRegistryListsBuiltins() {
        var formats = parseService.formats();
        assertThat(formats).extracting(RecipeBodyParseService.FormatInfo::format)
                .contains("JSON_STRUCTURED", "TEXT");
    }
}
