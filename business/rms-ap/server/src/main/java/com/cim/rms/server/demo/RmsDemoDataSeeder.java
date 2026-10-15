package com.cim.rms.server.demo;

import com.cim.rms.server.authz.DeviceAuthService;
import com.cim.rms.server.bypass.BypassService;
import com.cim.rms.server.device.DeviceAdminService;
import com.cim.rms.server.device.DeviceAreaRepository;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.device.DeviceTypeRepository;
import com.cim.rms.server.ecsv.EcSvService;
import com.cim.rms.server.golden.GoldenService;
import com.cim.rms.server.integration.IntegrationService;
import com.cim.rms.server.qualification.QualificationService;
import com.cim.rms.server.recipe.BodyFormat;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.rms.server.recipe.RecipeService;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
import com.cim.rms.server.signoff.RecipeSignoffService;
import com.cim.rms.server.signoff.SignRoute;
import com.cim.rms.server.spec.ParamSpecService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * RMS 演示数据灌入（便于 H2 场景「开箱即有数据」）。
 *
 * <p><b>幂等</b>：以配方业务键为准，任一示例配方已存在即整体跳过；可反复重启安全。</p>
 *
 * <p><b>启用条件</b>：{@code cim.rms.demo.seed-enabled}（缺省 true）且 {@code cim.auth.enabled}
 * 不为 false——测试关认证时自动不灌，避免污染断言；生产如需关闭显式设 false。</p>
 *
 * <p>灌入内容：机型/区域/设备台账、配方（含主/子层级、Golden、多版本与状态流转）、参数规格与模板、
 * 资格矩阵、Bypass、Golden 机差、EC/SV 规格与一次校验、设备授权，以及一条 Hold/报警与一条待签核。</p>
 */
@Slf4j
@Component
@Order(Integer.MAX_VALUE - 100) // 晚于控制台种子，早于业务
@ConditionalOnProperty(name = "cim.rms.demo.seed-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class RmsDemoDataSeeder implements ApplicationRunner {

    private final DeviceAdminService deviceAdmin;
    private final RecipeService recipeService;
    private final ParamSpecService specService;
    private final QualificationService qualificationService;
    private final BypassService bypassService;
    private final GoldenService goldenService;
    private final EcSvService ecSvService;
    private final DeviceAuthService deviceAuthService;
    private final IntegrationService integrationService;
    private final RecipeSignoffService signoffService;
    private final com.cim.rms.server.integration.ToolRecipeMapService ppidMapService;
    private final com.cim.rms.server.integration.RecipeDownloadService downloadService;
    private final com.cim.rms.server.integration.EquipmentEventService equipmentEventService;
    private final com.cim.rms.server.integration.ToolInventoryService toolInventoryService;
    private final com.cim.rms.server.integration.RmsEquipmentEventRepository eventRepository;
    private final com.cim.rms.server.integration.ToolRecipeInventoryRepository inventoryRepository;

    private final DeviceTypeRepository typeRepository;
    private final DeviceAreaRepository areaRepository;
    private final DeviceRepository deviceRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;

    @org.springframework.beans.factory.annotation.Value("${cim.auth.enabled:true}")
    private boolean authEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!authEnabled) {
            log.info("[rms-demo] 认证已关闭（测试场景），跳过演示数据灌入");
            return;
        }
        try {
            boolean seeded = seed();
            if (seeded) {
                log.info("[rms-demo] 演示数据灌入完成：机型 {} · 区域 {} · 设备 {} · 配方 {} · 规格 {}",
                        typeRepository.count(), areaRepository.count(), deviceRepository.count(),
                        recipeRepository.count(), specService.list(firstRecipeId()).size());
                log.info("[rms-demo] 下发任务（IF-E2）已灌入 3 条：进行中 1 · 已闭环 1 · 回读漂移 1");
                log.info("[rms-demo] 设备侧治理已灌入：GEM 事件 {} 条（含 1 漂移 / 1 孤儿 / 1 校验失败）"
                                + " · 机台配方清单 {} 行（含 OK / DRIFT / ORPHAN）",
                        eventRepository.count(), inventoryRepository.count());
            } else {
                log.info("[rms-demo] 演示数据已存在，跳过");
            }
        } catch (Exception e) {
            // 演示数据不影响系统可用性；失败仅告警（不阻断启动）
            log.error("[rms-demo] 演示数据灌入失败（不影响启动）：{}", e.toString(), e);
        }
    }

    private String firstRecipeId() {
        return recipeRepository.findByCodeAndDeletedFalse("RCP-ETCH-STD").map(r -> r.getId()).orElse("");
    }

    /** @return 是否实际灌入 */
    private boolean seed() {
        if (recipeRepository.findByCodeAndDeletedFalse("RCP-ETCH-STD").isPresent()) {
            return false;
        }

        // ---------- 机型 ----------
        String etch = deviceAdmin.createType("ETCH", "等离子刻蚀机", "干法刻蚀设备", "APPLIED", "Centura").id();
        String cvd = deviceAdmin.createType("CVD", "化学气相沉积", "薄膜沉积设备", "LAM", "Vector").id();
        String pvd = deviceAdmin.createType("PVD", "物理气相沉积", "金属溅射设备", "AMAT", "Endura").id();

        // ---------- 区域（两级） ----------
        String fab1 = deviceAdmin.createArea("FAB1", "一厂", null, 0, "主体厂房").id();
        String etchArea = deviceAdmin.createArea("FAB1-ETCH", "刻蚀区", fab1, 10, null).id();
        String filmArea = deviceAdmin.createArea("FAB1-FILM", "薄膜区", fab1, 20, null).id();

        // ---------- 设备台账 ----------
        String etch01 = deviceAdmin.createDevice("ETCH01", "刻蚀机 01", etch, etchArea, "10.20.1.11", null).id();
        String etch02 = deviceAdmin.createDevice("ETCH02", "刻蚀机 02", etch, etchArea, "10.20.1.12", null).id();
        String cvd01 = deviceAdmin.createDevice("CVD01", "沉积机 01", cvd, filmArea, "10.20.2.11", null).id();
        String pvd01 = deviceAdmin.createDevice("PVD01", "溅射机 01", pvd, filmArea, "10.20.3.11", null).id();

        // ---------- 配方（含版本与状态） ----------
        // 1) Golden 标准刻蚀配方（ACTIVE）
        var std = recipeService.create("RCP-ETCH-STD", "刻蚀标准配方", etch, etchArea, null, true,
                "Golden 基准配方", BodyFormat.TEXT.name(), b64("STEP=1\nTEMP=70\nPOWER=1000\nGAS_CF4=120\nMODE=A"), null);
        activateLatest(std.id());

        // 2) 刻蚀硬掩膜配方（v1 ACTIVE，v2 DRAFT）
        var hard = recipeService.create("RCP-ETCH-HARD", "刻蚀硬掩膜配方", etch, etchArea, null, false,
                "硬掩膜工艺", BodyFormat.TEXT.name(), b64("STEP=1\nTEMP=75\nPOWER=1200\nMODE=B"), null);
        activateLatest(hard.id());
        recipeService.newVersion(hard.id(), BodyFormat.TEXT,
                b64("STEP=1\nTEMP=78\nPOWER=1250\nMODE=B"), null, null, "调整温度与功率");

        // 3) 沉积主配方（ACTIVE）+ 子配方（DRAFT，演示层级）
        var cvdMain = recipeService.create("RCP-CVD-MAIN", "沉积主配方", cvd, filmArea, null, false,
                "主配方", BodyFormat.TEXT.name(), b64("STEP=1\nTEMP=400\nPRESSURE=2.0"), null);
        activateLatest(cvdMain.id());
        recipeService.create("RCP-CVD-SUB", "沉积子配方", cvd, filmArea, cvdMain.id(), false,
                "子配方（继承主配方上下文）", BodyFormat.JSON_STRUCTURED.name(),
                b64("{\"step\":1,\"temp\":400,\"gas\":\"SiH4\"}"), null);

        // 4) 溅射配方（发起签核 → PENDING_SIGN）
        var pvdRecipe = recipeService.create("RCP-PVD-COMMON", "溅射通用配方", pvd, filmArea, null, false,
                "待签核", BodyFormat.TEXT.name(), b64("STEP=1\nPOWER=2000\nTARGET=Al"), null);
        RecipeVersion pvdV1 = latestVersion(pvdRecipe.id());
        signoffService.submit(pvdRecipe.id(), pvdV1.getId(), "首次上线，走签核流程", SignRoute.SINGLE);

        // ---------- 参数规格（四类 + 双轨容差） ----------
        String stdId = std.id();
        specService.upsert(stdId, spec("CHAMBER.TEMP", "腔体温度", "RANGE", "60", "80", null, null, null, "℃"));
        specService.upsert(stdId, spec("RF.POWER", "射频功率", "TOLERANCE", null, null, "1000", 5.0, 30.0, "W"));
        specService.upsert(stdId, spec("GAS.CF4", "CF4 流量", "TARGET", null, null, "120", null, null, "sccm"));
        specService.upsert(stdId, spec("STEP.MODE", "工艺模式", "LIST_IN", null, null, null, null, null, null));
        // 补充枚举取值
        specService.upsert(stdId, new ParamSpecService.SpecInput("STEP.MODE", "工艺模式", "LIST_IN", "TEXT",
                null, null, null, "A,B,C", null, null, null, true));

        // ---------- 规格模板 + 应用到配方 ----------
        specService.saveTemplate(null, new ParamSpecService.TemplateInput("TPL-ETCH", "刻蚀通用模板", "ETCH",
                "刻蚀机通用参数规格", List.of(
                new ParamSpecService.TemplateItemInput("CHAMBER.TEMP", "腔体温度", "RANGE", "NUMBER",
                        null, "60", "80", null, null, null, "℃", true),
                new ParamSpecService.TemplateItemInput("RF.POWER", "射频功率", "TOLERANCE", "NUMBER",
                        null, null, null, null, 5.0, 30.0, "W", true))));
        specService.applyTemplate(hard.id(), "TPL-ETCH");

        // ---------- 资格矩阵 ----------
        qualificationService.grant(stdId, "LATEST_ACTIVE", null, null, etch, null,
                "AQ-2026-001 刻蚀机型资格认定", null, null);
        qualificationService.grant(cvdMain.id(), "LATEST_ACTIVE", null, cvd01, null, null,
                "AQ-2026-002 沉积机 01 资格", null, null);

        // ---------- Bypass ----------
        String subId = recipeRepository.findByCodeAndDeletedFalse("RCP-CVD-SUB").map(r -> r.getId()).orElse(null);
        if (subId != null) {
            bypassService.create("RECIPE", subId, "BOTH", "调试期临时豁免比对与参数检查",
                    LocalDateTime.now().minusDays(1).atZone(java.time.ZoneId.systemDefault()).toInstant(),
                    LocalDateTime.now().plusDays(7).atZone(java.time.ZoneId.systemDefault()).toInstant());
        }

        // ---------- Golden 机差覆盖层（提交 + 批准） ----------
        var go = goldenService.submit(etch01, stdId, "CHAMBER.TEMP", "58", "82", "ETCH01 机差：温度偏低");
        goldenService.approve(go.id(), "机差确认，批准");

        // ---------- EC/SV 规格与一次校验 ----------
        ecSvService.saveSpec(null, new EcSvService.SpecInput("RECIPE", stdId, "EC", null, "CHAMBER.TEMP",
                "RANGE", null, "60", "80", null, null, "℃", true));
        ecSvService.saveSpec(null, new EcSvService.SpecInput("RECIPE", stdId, "SV", "SV_LOADLOCK_PRESSURE",
                "LOADLOCK.PRESSURE", "TOLERANCE", "1.0", null, null, 10.0, 0.1, "Torr", true));
        ecSvService.upload(etch01, "RECIPE", stdId, "EC", Map.of("CHAMBER.TEMP", "70"));
        ecSvService.upload(etch01, "RECIPE", stdId, "SV", Map.of("LOADLOCK.PRESSURE", "1.35"));

        // ---------- 设备授权（按机型授予） ----------
        deviceAuthService.grant("admin", "USER", null, etch, null, true);
        deviceAuthService.grant("admin", "USER", pvd01, null, null, true);

        // ---------- 机台 PPID ↔ 配方映射（D7：机台级 + 机型级各一） ----------
        ppidMapService.register(etch01, null, "ETCH_STD_01", stdId);
        ppidMapService.register(null, etch, "ETCH_STD", stdId);

        // ---------- W7：多腔机台腔体 + JSON 结构化配方 + PPID 绑腔体 ----------
        deviceAdmin.createChamber(cvd01, "CH-A", "沉积腔 A", 1, null);
        deviceAdmin.createChamber(cvd01, "CH-B", "沉积腔 B", 2, null);
        String chamberA = deviceAdmin.listChambers(cvd01).stream()
                .filter(c -> "CH-A".equals(c.code())).findFirst().orElseThrow().id();
        var dep = recipeService.create("RCP-CVD-DEP", "沉积多腔配方", cvd, filmArea, null, false,
                "JSON 结构化配方（W7 解析演示）", BodyFormat.JSON_STRUCTURED.name(),
                b64("{\"meta\":{\"process\":\"DEP\",\"tool\":\"CVD01\"},"
                        + "\"params\":{"
                        + "\"CHAMBER.TEMP\":{\"value\":\"400\",\"unit\":\"C\",\"min\":\"380\",\"max\":\"420\"},"
                        + "\"CHAMBER.PRESSURE\":{\"value\":\"2.0\",\"unit\":\"Torr\",\"min\":\"1.5\",\"max\":\"2.5\"}},"
                        + "\"steps\":[{\"no\":1,\"name\":\"沉积\",\"params\":[\"CHAMBER.TEMP\",\"CHAMBER.PRESSURE\"]}],"
                        + "\"subRecipes\":[{\"name\":\"DEP-SUB\",\"ref\":\"RCP-CVD-SUB\",\"stepNo\":1}]}"), null);
        activateLatest(dep.id());
        qualificationService.grant(dep.id(), "LATEST_ACTIVE", null, cvd01, null, null,
                "AQ-2026-003 沉积多腔配方资格", null, null);
        // 腔体级装载：CVD_DEP_A → CH-A（W7 设备配方页可见腔体维度）
        ppidMapService.register(cvd01, null, "CVD_DEP_A", dep.id(), chamberA);

        // ---------- Hold + 报警（演示集成监控） ----------
        integrationService.applyHold(etch02, stdId, "RCP-ETCH-STD", "在线比对失败自动 Hold", "ONLINE_COMPARE");
        integrationService.raiseAlarm("RMS_COMPARE_FAIL", "MAJOR", etch02, "LOT-20261012-001", "RCP-ETCH-STD",
                "在线比对失败：Body 不一致 · 参数失败 2", "corr-demo-0001");
        integrationService.raiseAlarm("RMS_DRIFT", "MINOR", pvd01, null, "RCP-PVD-COMMON",
                "巡检发现机台侧配方漂移", "corr-demo-0002");

        // ---------- IF-E2 下发任务（阶段机三种收尾各一，便于页面/接口联调） ----------
        seedDownloadJobs(etch01, stdId);

        // ---------- W6 设备侧配方治理（GEM 事件 + 机台配方清单） ----------
        seedEquipmentGovernance(etch01, stdId);

        return true;
    }

    /**
     * 灌入设备侧治理演示数据：GEM 事件 3 条（漂移 / 孤儿 / 机台校验失败）+ 机台配方清单一轮核对。
     *
     * <p>目的：让「设备事件」「机台配方清单」两个面板一进系统就有真实语义的数据，
     * 而不是空表（否则演示/联调看不出这三类结论长什么样）。</p>
     */
    private void seedEquipmentGovernance(String deviceId, String recipeId) {
        String hash = activeHash(recipeId);
        // 再登记一条映射，便于演示「映射存在但机台 HASH 不符」= DRIFT
        ppidMapService.register(deviceId, null, "ETCH_STD_ALT", recipeId);

        // ① 机台侧编辑已纳管配方 → 漂移判定 + 默认挂 Hold
        equipmentEventService.handle(new com.cim.rms.server.integration.EquipmentEventService
                .EquipmentEventReq("demo-evt-0001", deviceId, "PP_CHANGE", "ETCH_STD_01", "EDITED",
                "operatorA", "corr-demo-evt-0001", null, null,
                java.time.Instant.now().minusSeconds(300), null));
        // ② 机台侧出现未纳管配方 → 孤儿信号（RMS_ORPHAN_PPID）
        equipmentEventService.handle(new com.cim.rms.server.integration.EquipmentEventService
                .EquipmentEventReq("demo-evt-0002", deviceId, "PP_CHANGE", "ETCH_ROGUE_9", "CREATED",
                "operatorB", "corr-demo-evt-0002", null, null,
                java.time.Instant.now().minusSeconds(200), null));
        // ③ 机台校验下发配方失败 → RMS_VERIF_FAIL + 在途任务判失败
        equipmentEventService.handle(new com.cim.rms.server.integration.EquipmentEventService
                .EquipmentEventReq("demo-evt-0003", deviceId, "PP_VERIFICATION_FAILED", "ETCH_STD_01",
                null, "eap", "corr-demo-evt-0003", null, "STEP3 温度超出机台允许范围",
                java.time.Instant.now().minusSeconds(120), null));

        // ④ 机台配方目录一轮核对：OK / DRIFT / ORPHAN 各一
        toolInventoryService.report(new com.cim.rms.server.integration.ToolInventoryService
                .InventoryReportReq(deviceId, "corr-demo-inv-0001", java.time.Instant.now(),
                java.util.List.of(
                        new com.cim.rms.server.integration.ToolInventoryService
                                .InventoryItem("ETCH_STD_01", hash),
                        new com.cim.rms.server.integration.ToolInventoryService
                                .InventoryItem("ETCH_STD_ALT", "aa".repeat(32)),
                        new com.cim.rms.server.integration.ToolInventoryService
                                .InventoryItem("ETCH_ROGUE_9", null))));
    }

    /** 取配方生效版 Body HASH（与 IF-E2 下发基准口径一致）。 */
    private String activeHash(String recipeId) {
        com.cim.rms.server.recipe.Recipe r = recipeRepository.findById(recipeId).orElse(null);
        if (r == null || r.getActiveVersionId() == null) {
            return null;
        }
        return versionRepository.findById(r.getActiveVersionId())
                .map(RecipeVersion::getBodyHash).orElse(null);
    }

    /**
     * 灌 3 条下发任务，覆盖阶段机的三种收尾：
     * ① 进行中（TOOL_ACKED，未闭环）；② 回读校验通过（VERIFIED）；③ 回读不符（FAILED + RMS_DRIFT 报警）。
     */
    private void seedDownloadJobs(String deviceId, String recipeId) {
        // 取「生效版本」——与 IntegrationService.downloadRecipe 的取版口径一致，
        // 否则灌进去的 bodyHash 与真实下发时的基准对不上。
        var recipe = recipeRepository.findById(recipeId).orElseThrow();
        RecipeVersion active = recipe.getActiveVersionId() == null
                ? latestVersion(recipeId)
                : versionRepository.findById(recipe.getActiveVersionId()).orElseGet(() -> latestVersion(recipeId));
        String hash = active.getBodyHash();

        // ① 进行中：GRANTED → SENT_TO_TOOL → TOOL_ACKED
        downloadService.open("demo-dl-0001", deviceId, recipeId, active.getId(), "ETCH_STD_01",
                "ON_IDLE", null, "LOT-20261012-001", "eap-demo", hash);
        downloadService.advance("demo-dl-0001", com.cim.rms.server.integration.DownloadStage.GRANTED,
                null, null, null);
        downloadService.advance("demo-dl-0001", com.cim.rms.server.integration.DownloadStage.SENT_TO_TOOL,
                "ACK", null, null);
        downloadService.advance("demo-dl-0001", com.cim.rms.server.integration.DownloadStage.TOOL_ACKED,
                "ACK", null, null);

        // ② 闭环：一路到 VERIFIED（回读 HASH 一致）
        downloadService.open("demo-dl-0002", deviceId, recipeId, active.getId(), "ETCH_STD_01",
                "IMMEDIATE", null, null, "mes-demo", hash);
        for (com.cim.rms.server.integration.DownloadStage s : java.util.List.of(
                com.cim.rms.server.integration.DownloadStage.GRANTED,
                com.cim.rms.server.integration.DownloadStage.SENT_TO_TOOL,
                com.cim.rms.server.integration.DownloadStage.TOOL_ACKED)) {
            downloadService.advance("demo-dl-0002", s, "ACK", null, null);
        }
        downloadService.advance("demo-dl-0002", com.cim.rms.server.integration.DownloadStage.VERIFIED,
                "ACK", null, hash);

        // ③ 回读不符 → FAILED + RMS_DRIFT（演示「漂移报警」闭环）
        downloadService.open("demo-dl-0003", deviceId, recipeId, active.getId(), "ETCH_STD_01",
                "BATCH", "BATCH-20261012-01", null, "mes-demo", hash);
        for (com.cim.rms.server.integration.DownloadStage s : java.util.List.of(
                com.cim.rms.server.integration.DownloadStage.GRANTED,
                com.cim.rms.server.integration.DownloadStage.SENT_TO_TOOL,
                com.cim.rms.server.integration.DownloadStage.TOOL_ACKED)) {
            downloadService.advance("demo-dl-0003", s, "ACK", null, null);
        }
        downloadService.advance("demo-dl-0003", com.cim.rms.server.integration.DownloadStage.VERIFIED,
                "ACK", null, "00000000000000000000000000000000000000000000000000000000000000ff");
    }

    private void activateLatest(String recipeId) {
        RecipeVersion v = latestVersion(recipeId);
        recipeService.activate(recipeId, v.getId());
    }

    private RecipeVersion latestVersion(String recipeId) {
        return versionRepository.findFirstByRecipeIdAndDeletedFalseOrderByVersionNoDesc(recipeId)
                .orElseThrow(() -> new IllegalStateException("配方无版本: " + recipeId));
    }

    private static ParamSpecService.SpecInput spec(String key, String name, String type,
                                                   String min, String max, String target,
                                                   Double pct, Double abs, String unit) {
        boolean listIn = "LIST_IN".equals(type);
        return new ParamSpecService.SpecInput(key, name, type, listIn ? "TEXT" : "NUMBER",
                target, min, max, null, pct, abs, unit, true);
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
