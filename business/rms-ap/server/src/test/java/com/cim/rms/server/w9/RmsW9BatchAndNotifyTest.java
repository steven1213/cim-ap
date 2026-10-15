package com.cim.rms.server.w9;

import com.cim.rms.server.device.DeviceAdminService;
import com.cim.rms.server.integration.IntegrationService;
import com.cim.rms.server.integration.RecipeDownloadService;
import com.cim.rms.server.notification.NotificationService;
import com.cim.rms.server.qualification.QualificationService;
import com.cim.rms.server.recipe.RecipeService;
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
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W9 集成测试：批量 Hold（设备级/配方级/幂等/释放）、批量下发（部分成功 + 幂等重放）、
 * 签核通知（提交→审批受众广播、决定→发起人）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:rms-w9-it;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.auth.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,"
                + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RmsW9BatchAndNotifyTest {

    @Autowired
    DeviceAdminService deviceAdmin;
    @Autowired
    RecipeService recipeService;
    @Autowired
    QualificationService qualificationService;
    @Autowired
    IntegrationService integrationService;
    @Autowired
    RecipeDownloadService downloadService;
    @Autowired
    RecipeSignoffService signoffService;
    @Autowired
    NotificationService notificationService;

    private static String typeId;
    private static String d1;
    private static String d2;
    private static String r1;

    static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static final String BODY = """
            {"meta":{"process":"DEP"},"params":{"TEMP":{"value":"75"}},"steps":[]}""";

    @Test
    @Order(1)
    void setup() {
        typeId = deviceAdmin.createType("T-W9", "W9 机型", null, null, null).id();
        d1 = deviceAdmin.createDevice("W9-D01", "机台一", typeId, null, null, null).id();
        d2 = deviceAdmin.createDevice("W9-D02", "机台二", typeId, null, null, null).id();
        r1 = recipeService.create("RCP-W9-A", "批量测试配方", typeId, null, null, false, null,
                "JSON_STRUCTURED", b64(BODY), null).id();
    }

    @Test
    @Order(2)
    void batchApplyDeviceLevelHold() {
        IntegrationService.BatchOpResult r = integrationService.batchHold(
                new IntegrationService.BatchHoldReq("APPLY", List.of(d2), null, null, "气体事件", "MANUAL"));
        assertThat(r.total()).isEqualTo(1);
        assertThat(r.ok()).isEqualTo(1);
        assertThat(integrationService.listHolds(true))
                .anyMatch(h -> d2.equals(h.deviceId()) && h.recipeId() == null && h.active());
    }

    @Test
    @Order(3)
    void batchApplyRecipeLevelHoldAcrossDevices() {
        IntegrationService.BatchOpResult r = integrationService.batchHold(
                new IntegrationService.BatchHoldReq("APPLY", List.of(d1, d2), null, List.of(r1),
                        "配方缺陷", null));
        // D2 已有设备级 Hold（ALL），配方级为新增 1 条；D1 新增 1 条 → 共 2 条配方级
        assertThat(r.ok()).isEqualTo(2);
        List<IntegrationService.HoldView> active = integrationService.listHolds(true).stream()
                .filter(h -> r1.equals(h.recipeId())).toList();
        assertThat(active).hasSize(2);
    }

    @Test
    @Order(4)
    void batchApplyIsIdempotent() {
        integrationService.batchHold(new IntegrationService.BatchHoldReq(
                "APPLY", List.of(d1, d2), null, List.of(r1), "配方缺陷", null));
        long count = integrationService.listHolds(true).stream()
                .filter(h -> r1.equals(h.recipeId())).count();
        assertThat(count).isEqualTo(2); // 幂等：重复挂不叠加
    }

    @Test
    @Order(5)
    void batchRelease() {
        IntegrationService.BatchOpResult r = integrationService.batchHold(
                new IntegrationService.BatchHoldReq("RELEASE", List.of(d1, d2), null, List.of(r1),
                        "缺陷已修复", null));
        assertThat(r.ok()).isEqualTo(2);
        assertThat(integrationService.listHolds(true).stream()
                .filter(h -> r1.equals(h.recipeId())).count()).isZero();
    }

    @Test
    @Order(6)
    void batchDownloadPartialSuccessAndIdempotentReplay() {
        // v1 DRAFT：先激活才有资格走 ACCEPTED
        String v1 = recipeService.versionsOf(r1).get(0).getId();
        recipeService.activate(r1, v1);
        qualificationService.grant(r1, "LATEST_ACTIVE", null, d1, null, null, "W9 测试",
                null, null);

        String batchId = "W9BATCH1";
        IntegrationService.BatchOpResult first = integrationService.batchDownload(
                new IntegrationService.BatchDownloadReq("IMMEDIATE", batchId, "w9-tester", List.of(
                        new IntegrationService.BatchDownloadItem(d1, r1, null, null, null),
                        new IntegrationService.BatchDownloadItem("NO-SUCH-TOOL", r1, null, null, null))));
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.ok()).isEqualTo(1); // D1×R1 受理
        assertThat(first.failed()).isEqualTo(1); // 未知设备计入失败明细
        assertThat(first.items().get(1).ok()).isFalse();

        int jobsBefore = downloadService.list(null, batchId, null, false).size();
        // 同批次重放：correlationId 相同 → 幂等回放，任务数不增
        IntegrationService.BatchOpResult replay = integrationService.batchDownload(
                new IntegrationService.BatchDownloadReq("IMMEDIATE", batchId, "w9-tester", List.of(
                        new IntegrationService.BatchDownloadItem(d1, r1, null, null, null),
                        new IntegrationService.BatchDownloadItem("NO-SUCH-TOOL", r1, null, null, null))));
        assertThat(replay.ok()).isEqualTo(1);
        assertThat(downloadService.list(null, batchId, null, false).size()).isEqualTo(jobsBefore);
    }

    @Test
    @Order(7)
    void signoffSubmitNotifiesApproverAudience() {
        // Order(6) 已把 v1 激活；新开 DRAFT 版本走签核
        String versionId = recipeService.newVersion(r1,
                com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED, b64(BODY),
                null, null, "通知测试新版本").getId();
        String signoffId = signoffService.submit(r1, versionId, "W9 通知测试", SignRoute.SINGLE).id();
        // 有审批权的人可见（audience=APPROVER）
        NotificationService.NotificationPage page =
                notificationService.visible("any-approver", true, false, false);
        NotificationService.NotificationView submitted = page.items().stream()
                .filter(n -> "SIGNOFF_SUBMITTED".equals(n.type()) && signoffId.equals(n.refId()))
                .findFirst().orElse(null);
        assertThat(submitted).isNotNull();
        assertThat(submitted.read()).isFalse();
        // 无审批权的人不可见该受众通知
        NotificationService.NotificationPage denied =
                notificationService.visible("plain-operator", false, false, false);
        assertThat(denied.items().stream()
                .noneMatch(n -> "SIGNOFF_SUBMITTED".equals(n.type()) && signoffId.equals(n.refId())))
                .isTrue();
    }

    @Test
    @Order(8)
    void decisionNotifiesInitiatorAndMarkRead() {
        // Order(7) 已把 v1 送入 PENDING_SIGN；新版本走完整「提交→批准」链路
        String versionId = recipeService.newVersion(r1,
                com.cim.rms.server.recipe.BodyFormat.JSON_STRUCTURED, b64(BODY),
                null, null, "W9 决定通知新版本").getId();
        String signoffId = signoffService.submit(r1, versionId, "W9 决定通知", SignRoute.SINGLE).id();
        String initiator = signoffService.detail(signoffId).initiator();
        signoffService.approve(signoffId, "同意");

        NotificationService.NotificationPage page =
                notificationService.visible(initiator == null ? "system" : initiator, false, false, false);
        NotificationService.NotificationView approved = page.items().stream()
                .filter(n -> "SIGNOFF_APPROVED".equals(n.type()) && signoffId.equals(n.refId()))
                .findFirst().orElse(null);
        assertThat(approved).isNotNull();
        assertThat(approved.recipient()).isEqualTo(initiator);

        // 已读幂等：标记后 read=true，重复标记不报错
        notificationService.markRead(approved.id(), initiator == null ? "system" : initiator);
        notificationService.markRead(approved.id(), initiator == null ? "system" : initiator);
        NotificationService.NotificationPage after =
                notificationService.visible(initiator == null ? "system" : initiator, false, false, true);
        assertThat(after.items().stream().noneMatch(n -> n.id().equals(approved.id()))).isTrue();
        assertThat(after.unread()).isZero();
    }
}
