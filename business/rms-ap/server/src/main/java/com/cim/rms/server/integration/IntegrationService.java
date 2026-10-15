package com.cim.rms.server.integration;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.bypass.BypassService;
import com.cim.rms.server.compare.RecipeCompareService;
import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.golden.GoldenService;
import com.cim.rms.server.qualification.QualificationService;
import com.cim.rms.server.recipe.BodyFormat;
import com.cim.rms.server.recipe.Recipe;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.rms.server.recipe.RecipeService;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
import com.cim.rms.server.recipe.RecipeVersionStatus;
import com.cim.rms.server.spec.ParamSpecService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 系统间接口服务（IF-E1 上传 / IF-E2 下载 / IF-E3 在线比对 / IF-M1 Run 校验 / IF-A1 报警）。
 *
 * <p>RMS 不与设备直连：这里把 EAP/MES 的请求落成 RMS 侧的真实业务动作（建档、下载凭证、比对、
 * 资格与 Hold/Alarm），供集成联调与后续接入 cim-mq-starter 消息通道复用。所有动作留痕审计。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationService {

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final RecipeService recipeService;
    private final com.cim.rms.server.recipe.BodyStore bodyStore;
    private final RecipeCompareService compareService;
    private final DeviceRepository deviceRepository;
    private final QualificationService qualificationService;
    private final BypassService bypassService;
    private final ParamSpecService specService;
    private final GoldenService goldenService;
    private final ToolRecipeMapService toolRecipeMapService;
    private final RmsHoldRepository holdRepository;
    private final RmsAlarmRepository alarmRepository;
    private final IntegrationIdempotencyService idempotency;
    private final RecipeDownloadService downloadService;
    private final com.cim.rms.server.experiment.ExperimentService experimentService;
    private final org.springframework.transaction.PlatformTransactionManager txManager;
    private final RmsAuditService audit;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 幂等作用域：按接口隔离，避免不同接口的相同 correlationId 互相污染。 */
    private static final String SCOPE_UPLOAD = "IF_E1_UPLOAD";
    private static final String SCOPE_DOWNLOAD = "IF_E2_DOWNLOAD";
    private static final String SCOPE_ONLINE_COMPARE = "IF_E3_ONLINE_COMPARE";
    private static final String SCOPE_RUN_CHECK = "IF_M1_RUN_CHECK";

    // ==================== IF-E1 配方上传（EAP → RMS） ====================

    /**
     * IF-E1 上传入口：**幂等**（Req §5.2 IF-COM）。
     *
     * <p>EAP 超时重发同一 {@code correlationId} 时回放首次回执，<b>不会重复建版本</b>。
     * 幂等键取 {@code correlationId}；未携带时退化为内容哈希（PPID + 目标 + bodyHash + 原因），
     * 使「同内容重传」仍被去重。</p>
     */
    @Transactional
    public UploadResult uploadRecipe(UploadReq req) {
        String key = req.correlationId() != null && !req.correlationId().isBlank()
                ? req.correlationId()
                : IntegrationIdempotencyService.contentKey(
                        req.toolId(), req.ppid(), req.recipeUid(), req.bodyHash(), req.uploadReason(),
                        req.bodyBase64());
        return idempotency.execute(SCOPE_UPLOAD, key, UploadResult.class,
                () -> doUploadRecipe(req), UploadResult::result);
    }

    private UploadResult doUploadRecipe(UploadReq req) {
        Device device = resolveDevice(req.toolId());
        if (req.bodyBase64() == null || req.bodyBase64().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "bodyBase64 不能为空");
        }
        String actualHash = sha256(req.bodyBase64());
        if (req.bodyHash() != null && !req.bodyHash().isBlank()
                && !req.bodyHash().equalsIgnoreCase(actualHash)) {
            audit.record("RECIPE_UPLOAD", "FAILURE", device.getCode(), "HASH 不匹配（IF-COM 完整性）");
            return new UploadResult("HASH_MISMATCH", null, null, actualHash, List.of());
        }
        // 定位/建档目标配方（D7：EAP 只认机台 PPID；未指定 recipeUid 时以 ppid 映射/业务键解析）
        Recipe recipe = resolveRecipe(req.recipeUid(), req.ppid(), device);
        // D7：上传即登记/校正「机台 PPID → RMS 配方」映射
        if (req.ppid() != null && !req.ppid().isBlank()) {
            Recipe mapped = toolRecipeMapService.resolve(device.getId(), device.getDeviceTypeId(), req.ppid());
            if (mapped == null || !mapped.getId().equals(recipe.getId())) {
                toolRecipeMapService.register(device.getId(), null, req.ppid(), recipe.getId());
            }
        }
        BodyFormat fmt;
        try {
            fmt = req.bodyFormat() == null ? BodyFormat.TEXT : BodyFormat.valueOf(req.bodyFormat().toUpperCase());
        } catch (IllegalArgumentException e) {
            return new UploadResult("PARSE_FAIL", recipe.getCode(), null, actualHash, List.of());
        }
        RecipeVersion v = recipeService.newVersion(recipe.getId(), fmt, req.bodyBase64(), actualHash,
                req.paramSnapshot(), "EAP 上传(" + (req.uploadReason() == null ? "MANUAL" : req.uploadReason()) + ")");
        // FR-C5：上传自动参数检查。
        // W7：优先用「已落库的参数快照」——EAP 未显式携带 paramSnapshot 时，新版本创建阶段
        // 已尝试从 Body 解析出扁平参数并写入，故此处仍能完成规格判定（此前缺快照即跳过检查）。
        String snapshotForCheck = v.getParamSnapshot();
        List<ParamSpecService.SpecCheck> checks = snapshotForCheck == null || snapshotForCheck.isBlank()
                ? List.of() : specService.checkSnapshot(recipe.getId(), snapshotForCheck);
        String result = checks.isEmpty() ? "ACCEPTED" : "PARAM_CHECK_FAIL";
        audit.record("RECIPE_UPLOAD", "PARAM_CHECK_FAIL".equals(result) ? "FAILURE" : "SUCCESS",
                device.getCode(), "上传 " + recipe.getCode() + " " + fmt + " · 参数检查失败 " + checks.size());
        return new UploadResult(result, recipe.getCode(), v.getVersionNo(), actualHash, checks);
    }

    // ==================== IF-E2 配方下载（RMS → EAP → 设备） ====================

    /**
     * IF-E2 下发入口：**幂等** + **阶段机落库**（Req §5.2/§5.3）。
     *
     * <p>受理成功才登记幂等快照——资格未登记、版本未生效这类<b>前置闸门拒绝不缓存</b>，
     * 以便运维补齐资格后用同一 correlationId 重试即可放行（不落入陈旧拒绝陷阱）。
     * 受理成功后同键重试直接回放回执，不会产生第二个下发任务。</p>
     */
    @Transactional
    public DownloadResult downloadRecipe(DownloadReq req) {
        String corr = req.correlationId() != null && !req.correlationId().isBlank()
                ? req.correlationId()
                : IntegrationIdempotencyService.contentKey("DL", req.toolId(), req.ppidOnTool(),
                        req.recipeUid(), String.valueOf(req.version()), req.downloadMode());
        return idempotency.execute(SCOPE_DOWNLOAD, corr, DownloadResult.class,
                () -> doDownloadRecipe(req, corr), DownloadResult::result,
                r -> "ACCEPTED".equals(r.result()));
    }

    private DownloadResult doDownloadRecipe(DownloadReq req, String corr) {
        Device device = resolveDevice(req.toolId());
        Recipe recipe = requireRecipeByIdOrCode(req.recipeUid());
        RecipeVersion v = resolveVersion(recipe, req.version());
        // 资格闸门：未登记资格默认拒绝（FR-Q2，D8）；版本策略参与判定（FR-Q1）
        QualificationService.QualCheck qc = qualificationService.check(recipe.getId(), device.getId(), v.getId());
        if (!"QUALIFIED".equals(qc.result())) {
            raiseAlarm("RMS_NOT_QUALIFIED", "MAJOR", device.getId(), req.lotId(), recipe.getCode(),
                    "下载被资格矩阵拦截：" + qc.reason(), corr);
            audit.record("RECIPE_DOWNLOAD", "FAILURE", device.getCode(),
                    "拒绝下发（资格不符）" + recipe.getCode());
            return rejected(corr, recipe.getCode(), "NOT_QUALIFIED: " + qc.reason(), req.batchId());
        }
        // 下载已生效版本才放行；豁免通道二选一：Bypass（设备级/配方级）或
        // W11 实验通道（指定机台 × 指定版本 × 有效窗口内的 APPROVED 实验单，Req 缺口 #6）
        boolean experimentOk = v.getStatus() != RecipeVersionStatus.ACTIVE
                && experimentService.activeFor(device.getId(), v.getId());
        if (experimentOk) {
            audit.success("EXPERIMENT_DOWNLOAD", device.getCode(),
                    "实验通道放行 " + recipe.getCode() + " v" + v.getVersionNo());
        }
        if (v.getStatus() != RecipeVersionStatus.ACTIVE
                && !experimentOk
                && !bypassService.isBypassed(device.getId(), recipe.getId(), "COMPARE")) {
            audit.record("RECIPE_DOWNLOAD", "FAILURE", device.getCode(),
                    "拒绝下发（版本未生效）" + recipe.getCode() + " v" + v.getVersionNo());
            return rejected(corr, recipe.getCode(),
                    "VERSION_NOT_ACTIVE: v" + v.getVersionNo() + " 状态 " + v.getStatus(), req.batchId());
        }
        String mode = req.downloadMode() == null ? "IMMEDIATE" : req.downloadMode().toUpperCase();
        // 落下发任务（阶段机起点 QUEUED；同 correlationId 复用，不重复建任务）
        RecipeDownloadService.JobView job = downloadService.open(corr, device.getId(), recipe.getId(),
                v.getId(), req.ppidOnTool(), mode, req.batchId(), req.lotId(), req.requestor(),
                v.getBodyHash());
        audit.success("RECIPE_DOWNLOAD", device.getCode(),
                "下发受理 " + recipe.getCode() + " v" + v.getVersionNo() + " · " + job.downloadMode()
                        + (req.batchId() == null ? "" : " · batch=" + req.batchId()));
        return new DownloadResult(corr, job.stage(), "ACCEPTED", recipe.getCode(), v.getVersionNo(),
                // 下发到设备的是明文（D9 加密仅限存储层；传输与设备侧按 IF-E2 语义）
                bodyStore.readPlain(v), job.toolAck(), null, req.batchId());
    }

    /** 闸门拒绝回执（不建下发任务——未发生任何传输）。 */
    private static DownloadResult rejected(String corr, String recipeCode, String errorDesc, String batchId) {
        return new DownloadResult(corr, DownloadStage.FAILED.name(), "REJECTED", recipeCode, null,
                null, null, errorDesc, batchId);
    }

    // ==================== IF-E3 在线比对（EAP → RMS） ====================

    /** 闸门拒绝的 detail 前缀（用于判定「不缓存、可重试」语义）。 */
    private static final String GATE_NOT_QUALIFIED = "NOT_QUALIFIED";

    /**
     * IF-E3 在线比对入口：**幂等**（Req §5.2）。
     *
     * <p>去重的关键收益：EAP 超时重发不会重复挂 Hold、重复报 {@code RMS_COMPARE_FAIL}。
     * 资格闸门拒绝不缓存（运维补资格后可重试）。</p>
     */
    @Transactional
    public OnlineCompareResult onlineCompare(OnlineCompareReq req) {
        String corr = req.correlationId() != null && !req.correlationId().isBlank()
                ? req.correlationId()
                : IntegrationIdempotencyService.contentKey("CMP", req.toolId(), req.ppidOnTool(),
                        req.recipeUid(), req.bodyHash(), req.cmpMode(), req.baselineType());
        return idempotency.execute(SCOPE_ONLINE_COMPARE, corr, OnlineCompareResult.class,
                () -> doOnlineCompare(req, corr), OnlineCompareResult::cmpResult,
                r -> r.detail() == null || !r.detail().startsWith(GATE_NOT_QUALIFIED));
    }

    private OnlineCompareResult doOnlineCompare(OnlineCompareReq req, String corr) {
        Device device = resolveDevice(req.toolId());
        Recipe recipe = resolveRecipe(req.recipeUid(), req.ppidOnTool(), device);
        // FR-Q2：资格矩阵拦截（未登记资格默认拒绝，先于比对本身）
        RecipeVersion running = activeVersion(recipe);
        QualificationService.QualCheck qc = qualificationService.check(recipe.getId(), device.getId(),
                running == null ? null : running.getId());
        if (!"QUALIFIED".equals(qc.result())) {
            raiseAlarm("RMS_NOT_QUALIFIED", "MAJOR", device.getId(), req.lotId(), recipe.getCode(),
                    qc.reason(), corr);
            return new OnlineCompareResult(corr, "FAIL", recipe.getCode(), null,
                    List.of(), false, true, 0, GATE_NOT_QUALIFIED + ": " + qc.reason());
        }
        // Bypass：设备级/配方级豁免直接放行（FR-B1/B2）
        if (bypassService.isBypassed(device.getId(), recipe.getId(), "COMPARE")) {
            audit.success("ONLINE_COMPARE_BYPASSED", device.getCode(), "比对豁免 " + recipe.getCode());
            return new OnlineCompareResult(corr, "BYPASSED", recipe.getCode(), null,
                    List.of(), false, false, 0, "Bypass 豁免");
        }
        String mode = req.cmpMode() == null || req.cmpMode().isBlank()
                ? "FULL_BODY" : req.cmpMode().toUpperCase();
        boolean goldenBaseline = "GOLDEN".equalsIgnoreCase(req.baselineType());

        // 基准与规格来源：GOLDEN 时以同机型 Golden 配方为基准与规格所有者（FR-G1/G2）
        Recipe specOwner = recipe;
        RecipeVersion baseline;
        if (goldenBaseline) {
            Recipe golden = findGolden(recipe);
            specOwner = golden != null ? golden : recipe;
            baseline = golden == null ? null : activeVersion(golden);
        } else {
            baseline = resolveBaseline(recipe, req.baselineType(), req.baselineVersionId());
        }
        if (baseline == null) {
            return new OnlineCompareResult(corr, "FAIL", recipe.getCode(), null,
                    List.of(), false, false, 0, "基准版本不存在");
        }

        long t0 = System.currentTimeMillis();
        String actualHash = req.bodyHash() != null && !req.bodyHash().isBlank()
                ? req.bodyHash() : sha256(req.bodyBase64());
        boolean hashEqual = baseline.getBodyHash() != null && baseline.getBodyHash().equalsIgnoreCase(actualHash);
        Map<String, String> actualParams = actualParams(req);
        Map<String, String> baselineParams = parseParams(baseline.getParamSnapshot());

        List<ParamSpecService.SpecCheck> checks = new ArrayList<>();
        boolean pass;
        String detail;
        switch (mode) {
            case "PARAMETER" -> {
                if (actualParams.isEmpty()) {
                    pass = false;
                    detail = "机台未提供参数快照，无法做参数级比对";
                } else {
                    checks = new ArrayList<>(specService.check(specOwner.getId(), actualParams));
                    // FR-G2：Golden 比对时，该设备<b>已批准</b>的机差覆盖层（放宽区间）并入判定
                    if (goldenBaseline) {
                        checks.removeIf(c -> goldenService.withinOverride(device.getId(), recipe.getId(),
                                c.paramKey(), c.actual()));
                    }
                    pass = checks.isEmpty();
                    detail = pass ? null : "参数越界 " + checks.size() + " 项";
                }
            }
            case "STRUCTURE" -> {
                if (actualParams.isEmpty()) {
                    pass = false;
                    detail = "机台未提供参数快照，无法做结构级比对";
                } else {
                    Set<String> missing = new TreeSet<>(baselineParams.keySet());
                    missing.removeAll(actualParams.keySet());
                    Set<String> extra = new TreeSet<>(actualParams.keySet());
                    extra.removeAll(baselineParams.keySet());
                    for (String k : missing) {
                        checks.add(new ParamSpecService.SpecCheck(k, "存在", "缺失", "结构缺失"));
                    }
                    for (String k : extra) {
                        checks.add(new ParamSpecService.SpecCheck(k, "不存在", "多出", "结构多出"));
                    }
                    pass = checks.isEmpty();
                    detail = pass ? null : "结构差异 " + checks.size() + " 项";
                }
            }
            // Full Body（默认）：只按 Body 完整性判定（FR-C1）
            default -> {
                pass = hashEqual;
                detail = pass ? null : "Body 哈希不一致";
            }
        }

        boolean holdApplied = false;
        boolean alarmRaised = false;
        if (!pass) {
            applyHold(device.getId(), recipe.getId(), recipe.getCode(),
                    "在线比对失败（" + mode + " · baseline v" + baseline.getVersionNo() + "）", "ONLINE_COMPARE");
            holdApplied = true;
            raiseAlarm("RMS_COMPARE_FAIL", "MAJOR", device.getId(), req.lotId(), recipe.getCode(),
                    "在线比对失败[" + mode + "]：" + detail, corr);
            alarmRaised = true;
        }
        long ms = System.currentTimeMillis() - t0;
        audit.record("ONLINE_COMPARE", pass ? "SUCCESS" : "FAILURE", device.getCode(),
                recipe.getCode() + " · " + (pass ? "PASS" : "FAIL") + " · " + mode);
        return new OnlineCompareResult(corr, pass ? "PASS" : "FAIL", recipe.getCode(),
                baseline.getVersionNo(), checks, holdApplied, alarmRaised, ms, detail);
    }

    // ==================== IF-M1 Run 货配方校验（MES → RMS） ====================

    /**
     * IF-M1 Run 校验入口：**幂等**（Req §5.2）。
     *
     * <p>本接口只读 + 可选报警，去重可避免 MES 重试造成重复 {@code RMS_NOT_QUALIFIED} 报警；
     * 资格闸门拒绝不缓存（补资格后重试即可放行）。</p>
     */
    @Transactional
    public RunCheckResult runCheck(RunCheckReq req) {
        String corr = req.correlationId() != null && !req.correlationId().isBlank()
                ? req.correlationId()
                : IntegrationIdempotencyService.contentKey("RUN", req.toolId(), req.ppidOnTool(),
                        req.recipeUid(), req.lotId(), req.versionPolicy());
        return idempotency.execute(SCOPE_RUN_CHECK, corr, RunCheckResult.class,
                () -> doRunCheck(req, corr), RunCheckResult::checkResult,
                r -> !"NOT_QUALIFIED".equals(r.checkResult()));
    }

    private RunCheckResult doRunCheck(RunCheckReq req, String corr) {
        Device device = resolveDevice(req.toolId());
        Recipe recipe = resolveRecipe(req.recipeUid(), req.ppidOnTool(), device);
        RecipeVersion active = activeVersion(recipe);
        // FR-Q2 资格矩阵（含版本策略）：未登记即 NOT_QUALIFIED，默认拒绝
        QualificationService.QualCheck qc = qualificationService.check(recipe.getId(), device.getId(),
                active == null ? null : active.getId());
        if (!"QUALIFIED".equals(qc.result())) {
            if (Boolean.TRUE.equals(req.verifyRequired())) {
                raiseAlarm("RMS_NOT_QUALIFIED", "MAJOR", device.getId(), req.lotId(), recipe.getCode(),
                        qc.reason(), corr);
            }
            return new RunCheckResult("NOT_QUALIFIED", recipe.getCode(), null, qc.reason(), now());
        }
        if (!holdRepository.findByDeviceIdAndActiveTrueAndDeletedFalse(device.getId()).isEmpty()) {
            return new RunCheckResult("HOLD", recipe.getCode(), active == null ? null : active.getVersionNo(),
                    "设备存在未解除的 Hold", now());
        }
        if (active == null) {
            return new RunCheckResult("NOT_FOUND", recipe.getCode(), null, "配方无生效版本", now());
        }
        boolean exact = "EXACT_VERSION".equalsIgnoreCase(req.versionPolicy());
        if (exact && req.exactVersionId() != null && !req.exactVersionId().equals(active.getId())) {
            return new RunCheckResult("VERSION_MISMATCH", recipe.getCode(), active.getVersionNo(),
                    "期望版本与生效版本不一致", now());
        }
        audit.success("RUN_CHECK", device.getCode(), "Run 校验通过 " + recipe.getCode() + " v" + active.getVersionNo());
        return new RunCheckResult("QUALIFIED", recipe.getCode(), active.getVersionNo(), null, now());
    }

    // ==================== 批量操作（W9，Req 缺口 #2） ====================

    /**
     * 批量 Hold / 解除（W9：一次气体事件不再手动处理上百条）。
     *
     * <p><b>目标展开</b>：设备集合 = deviceIds ∪ deviceTypeIds 展开的该机型全部未删设备；
     * 配方集合 = recipeIds（id 或 code）。APPLY：配方集合非空 → 逐「设备×配方」对挂 Hold
     * （applyHold 幂等，重复不叠加）；配方集合为空 → 逐设备挂<b>设备级 Hold</b>（全配方拦截）。
     * RELEASE：按同一展开条件匹配 active Hold 逐条释放。</p>
     *
     * <p><b>逐条独立事务</b>：单项失败不影响其余（部分成功语义，与签核批量一致）。</p>
     */
    public BatchOpResult batchHold(BatchHoldReq req) {
        boolean apply = !"RELEASE".equalsIgnoreCase(req.action());
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "批量 Hold/解除必须填写原因（强制留痕）");
        }
        List<Device> devices = expandDevices(req.deviceIds(), req.deviceTypeIds());
        List<Recipe> recipes = expandRecipes(req.recipeIds());
        if (devices.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "目标设备为空（deviceIds/deviceTypeIds 至少一项有效）");
        }
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(txManager);
        List<BatchItem> items = new ArrayList<>();
        int ok = 0, failed = 0;
        for (Device d : devices) {
            if (recipes.isEmpty()) {
                if (apply) {
                    try {
                        tx.executeWithoutResult(st -> applyHold(d.getId(), null, null, req.reason(),
                                req.source() == null ? "MANUAL" : req.source()));
                        ok++;
                        items.add(new BatchItem(d.getCode(), "ALL", true, "已挂设备级 Hold"));
                    } catch (Exception e) {
                        failed++;
                        items.add(new BatchItem(d.getCode(), "ALL", false, msg(e)));
                    }
                } else {
                    for (RmsHold h : holdRepository.findByDeviceIdAndActiveTrueAndDeletedFalse(d.getId())) {
                        try {
                            tx.executeWithoutResult(st -> releaseHold(h.getId(), req.reason()));
                            ok++;
                            items.add(new BatchItem(d.getCode(), h.getRecipeCode() == null ? "ALL" : h.getRecipeCode(),
                                    true, "已解除"));
                        } catch (Exception e) {
                            failed++;
                            items.add(new BatchItem(d.getCode(), h.getRecipeCode(), false, msg(e)));
                        }
                    }
                }
                continue;
            }
            for (Recipe r : recipes) {
                if (apply) {
                    try {
                        tx.executeWithoutResult(st -> applyHold(d.getId(), r.getId(), r.getCode(),
                                req.reason(), req.source() == null ? "MANUAL" : req.source()));
                        ok++;
                        items.add(new BatchItem(d.getCode(), r.getCode(), true, "已挂 Hold"));
                    } catch (Exception e) {
                        failed++;
                        items.add(new BatchItem(d.getCode(), r.getCode(), false, msg(e)));
                    }
                } else {
                    boolean any = false;
                    for (RmsHold h : holdRepository.findByDeviceIdAndActiveTrueAndDeletedFalse(d.getId())) {
                        if (r.getId().equals(h.getRecipeId())) {
                            try {
                                tx.executeWithoutResult(st -> releaseHold(h.getId(), req.reason()));
                                ok++;
                                items.add(new BatchItem(d.getCode(), r.getCode(), true, "已解除"));
                            } catch (Exception e) {
                                failed++;
                                items.add(new BatchItem(d.getCode(), r.getCode(), false, msg(e)));
                            }
                            any = true;
                        }
                    }
                    if (!any) {
                        items.add(new BatchItem(d.getCode(), r.getCode(), true, "无生效 Hold，跳过"));
                    }
                }
            }
        }
        audit.success(apply ? "HOLD_BATCH_APPLIED" : "HOLD_BATCH_RELEASED",
                devices.size() + " 台设备 / " + (recipes.isEmpty() ? "设备级" : recipes.size() + " 配方"),
                req.reason() + "（成功 " + ok + " 失败 " + failed + "）");
        return new BatchOpResult(items.size(), ok, failed, items);
    }

    /**
     * 批量下发（W9）：逐条复用 {@link #downloadRecipe}（幂等 + 阶段机 + 资格闸门全继承），
     * 共用 {@code batchId} 供 {@code /downloads/batch-progress} 聚合；
     * correlationId = {@code batchId-序号}（同批次重放幂等）。逐条独立事务，部分成功。
     */
    public BatchOpResult batchDownload(BatchDownloadReq req) {
        if (req.items() == null || req.items().isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "请至少给一条下发目标");
        }
        String batchId = req.batchId() == null || req.batchId().isBlank()
                ? "BATCH-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase()
                : req.batchId();
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(txManager);
        List<BatchItem> items = new ArrayList<>();
        int ok = 0, failed = 0;
        int idx = 0;
        for (BatchDownloadItem it : req.items()) {
            idx++;
            String corr = batchId + "-" + idx;
            try {
                DownloadResult r = tx.execute(st -> downloadRecipe(new DownloadReq(
                        it.toolId(), it.ppidOnTool(), it.recipeUid(), it.version(),
                        req.downloadMode(), batchId, it.lotId(), req.requestor(), corr)));
                boolean accepted = r != null && "ACCEPTED".equals(r.result());
                if (accepted) {
                    ok++;
                } else {
                    failed++; // 闸门拒绝（资格/版本）计入失败明细，错误信息可见
                }
                items.add(new BatchItem(it.toolId(), it.recipeUid(), accepted,
                        accepted ? ("已受理 " + r.stage()) : ("拒绝：" + (r == null ? "未知" : r.errorDesc()))));
            } catch (Exception e) {
                failed++;
                items.add(new BatchItem(it.toolId(), it.recipeUid(), false, msg(e)));
            }
        }
        audit.success("DOWNLOAD_BATCH", batchId,
                req.items().size() + " 条 · 成功 " + ok + " 失败 " + failed);
        return new BatchOpResult(items.size(), ok, failed, items);
    }

    /** 设备展开：ids ∪ 机型展开（id 或 code 皆可）。 */
    private List<Device> expandDevices(List<String> deviceIds, List<String> deviceTypeIds) {
        Map<String, Device> out = new LinkedHashMap<>();
        if (deviceIds != null) {
            for (String id : deviceIds) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                Device d = deviceRepository.findById(id).orElse(null);
                if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
                    d = deviceRepository.findByCodeAndDeletedFalse(id).orElse(null);
                }
                if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
                    throw new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + id);
                }
                out.put(d.getId(), d);
            }
        }
        if (deviceTypeIds != null) {
            for (String tid : deviceTypeIds) {
                if (tid == null || tid.isBlank()) {
                    continue;
                }
                for (Device d : deviceRepository.findByDeviceTypeIdAndDeletedFalse(tid)) {
                    out.put(d.getId(), d);
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    /** 配方展开（id 或 code 皆可）。 */
    private List<Recipe> expandRecipes(List<String> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return List.of();
        }
        Map<String, Recipe> out = new LinkedHashMap<>();
        for (String id : recipeIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            Recipe r = requireRecipeByIdOrCode(id);
            out.put(r.getId(), r);
        }
        return new ArrayList<>(out.values());
    }

    private static String msg(Exception e) {
        return e instanceof BizException ? e.getMessage() : "处理失败: " + e.getMessage();
    }

    // ==================== Hold ====================

    @Transactional
    public HoldView applyHold(String deviceId, String recipeId, String recipeCode, String reason, String source) {
        for (RmsHold h : holdRepository.findByDeviceIdAndActiveTrueAndDeletedFalse(deviceId)) {
            if (recipeId == null ? h.getRecipeId() == null : recipeId.equals(h.getRecipeId())) {
                return toHoldView(h); // 幂等：已有生效 Hold 不重复
            }
        }
        RmsHold h = new RmsHold();
        h.setDeviceId(deviceId);
        h.setRecipeId(recipeId);
        h.setRecipeCode(recipeCode);
        h.setReason(reason);
        h.setSource(source == null ? "MANUAL" : source);
        h.setActive(true);
        RmsHold saved = holdRepository.save(h);
        audit.record("HOLD_APPLIED", "SUCCESS", recipeCode, reason);
        return toHoldView(saved);
    }

    @Transactional
    public HoldView releaseHold(String id, String reason) {
        RmsHold h = holdRepository.findById(id).orElse(null);
        if (h == null || Boolean.TRUE.equals(h.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "Hold 记录不存在: " + id);
        }
        h.setActive(false);
        h.setReleasedBy(RmsAuditService.currentActor());
        h.setReleasedAt(Instant.now());
        h.setReleaseReason(reason);
        RmsHold saved = holdRepository.save(h);
        audit.success("HOLD_RELEASED", h.getRecipeCode(), "解除 Hold：" + reason);
        return toHoldView(saved);
    }

    public List<HoldView> listHolds(boolean activeOnly) {
        List<RmsHold> rows = activeOnly ? holdRepository.findByActiveTrueAndDeletedFalse()
                : holdRepository.findByDeletedFalseOrderByCreateTimeDesc();
        return rows.stream().map(this::toHoldView).toList();
    }

    // ==================== Alarm ====================

    @Transactional
    public AlarmView raiseAlarm(String code, String severity, String deviceId, String lotId,
                                String recipeCode, String message, String correlationId) {
        RmsAlarm a = new RmsAlarm();
        a.setAlarmCode(code);
        a.setSeverity(severity == null ? "MAJOR" : severity.toUpperCase());
        a.setDeviceId(deviceId);
        a.setLotId(lotId);
        a.setRecipeCode(recipeCode);
        a.setMessage(message);
        a.setCorrelationId(correlationId);
        a.setOccurredAt(Instant.now());
        a.setAcknowledged(false);
        RmsAlarm saved = alarmRepository.save(a);
        audit.record("ALARM_RAISED", "SUCCESS", code, message);
        return toAlarmView(saved);
    }

    @Transactional
    public AlarmView acknowledgeAlarm(String id) {
        RmsAlarm a = alarmRepository.findById(id).orElse(null);
        if (a == null || Boolean.TRUE.equals(a.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "报警不存在: " + id);
        }
        a.setAcknowledged(true);
        return toAlarmView(alarmRepository.save(a));
    }

    public List<AlarmView> listAlarms(boolean unackOnly) {
        List<RmsAlarm> rows = unackOnly ? alarmRepository.findByAcknowledgedFalseAndDeletedFalse()
                : alarmRepository.findByDeletedFalseOrderByCreateTimeDesc();
        return rows.stream().map(this::toAlarmView).toList();
    }

    // ==================== helpers ====================

    private Device resolveDevice(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "toolId 不能为空");
        }
        Device d = deviceRepository.findById(toolId).orElse(null);
        if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
            d = deviceRepository.findByCodeAndDeletedFalse(toolId).orElse(null);
        }
        if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + toolId);
        }
        return d;
    }

    private Recipe resolveRecipe(String recipeUid, String ppid, Device device) {
        if (recipeUid != null && !recipeUid.isBlank()) {
            Recipe r = recipeRepository.findById(recipeUid).orElse(null);
            if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
                r = recipeRepository.findByCodeAndDeletedFalse(recipeUid).orElse(null);
            }
            if (r != null && !Boolean.TRUE.equals(r.getDeleted())) {
                return r;
            }
        }
        // D7：先按「机台/机型 PPID 映射」解析（业界惯例，PPID→UID 由 RMS 维护）
        if (ppid != null && !ppid.isBlank()) {
            Recipe byPpid = toolRecipeMapService.resolve(device.getId(), device.getDeviceTypeId(), ppid);
            if (byPpid != null) {
                return byPpid;
            }
        }
        String code = ppid != null && !ppid.isBlank() ? ppid : recipeUid;
        if (code == null || code.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "recipeUid/ppid 至少指定一项");
        }
        Recipe existing = recipeRepository.findByCodeAndDeletedFalse(code).orElse(null);
        if (existing != null) {
            return existing;
        }
        // 首次上传建档（Req 1/2：从设备上传 Recipe）
        Recipe r = new Recipe();
        r.setCode(code);
        r.setName(code);
        r.setDeviceTypeId(device.getDeviceTypeId());
        r.setAreaId(device.getAreaId());
        return recipeRepository.save(r);
    }

    private Recipe requireRecipeByIdOrCode(String key) {
        if (key == null || key.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "recipeUid 不能为空");
        }
        Recipe r = recipeRepository.findById(key).orElse(null);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            r = recipeRepository.findByCodeAndDeletedFalse(key).orElse(null);
        }
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + key);
        }
        return r;
    }

    private RecipeVersion resolveVersion(Recipe recipe, Integer versionNo) {
        if (versionNo == null) {
            RecipeVersion active = activeVersion(recipe);
            if (active != null) {
                return active;
            }
        } else {
            RecipeVersion v = versionRepository
                    .findByRecipeIdAndVersionNoAndDeletedFalse(recipe.getId(), versionNo).orElse(null);
            if (v != null) {
                return v;
            }
        }
        RecipeVersion latest = versionRepository
                .findFirstByRecipeIdAndDeletedFalseOrderByVersionNoDesc(recipe.getId())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "配方无任何版本: " + recipe.getCode()));
        return latest;
    }

    private RecipeVersion activeVersion(Recipe recipe) {
        if (recipe.getActiveVersionId() == null) {
            return null;
        }
        return versionRepository.findById(recipe.getActiveVersionId())
                .filter(v -> v.getStatus() == RecipeVersionStatus.ACTIVE).orElse(null);
    }

    private RecipeVersion resolveBaseline(Recipe recipe, String type, String versionId) {
        String t = type == null ? "ACTIVE" : type.toUpperCase();
        if ("VERSION".equals(t) && versionId != null && !versionId.isBlank()) {
            return versionRepository.findById(versionId).orElse(null);
        }
        if ("GOLDEN".equals(t)) {
            Recipe golden = findGolden(recipe);
            if (golden != null) {
                RecipeVersion gv = activeVersion(golden);
                if (gv != null) {
                    return gv;
                }
            }
        }
        return activeVersion(recipe);
    }

    private Recipe findGolden(Recipe recipe) {
        return recipeRepository.findByDeviceTypeIdAndDeletedFalse(recipe.getDeviceTypeId()).stream()
                .filter(Recipe::isGolden).findFirst().orElse(null);
    }

    /**
     * 取机台侧实际参数：优先用请求携带的 {@code paramSnapshot}；缺省时若 Body 为
     * {@code JSON_STRUCTURED} 则就地解析（无机型适配器时的参数级比对兜底，Req §1.2 P2）。
     */
    private Map<String, String> actualParams(OnlineCompareReq req) {
        Map<String, String> m = parseParams(req.paramSnapshot());
        if (!m.isEmpty()) {
            return m;
        }
        if ("JSON_STRUCTURED".equalsIgnoreCase(req.bodyFormat())
                && req.bodyBase64() != null && !req.bodyBase64().isBlank()) {
            try {
                String json = new String(java.util.Base64.getDecoder().decode(req.bodyBase64()), StandardCharsets.UTF_8);
                return parseParams(json);
            } catch (IllegalArgumentException e) {
                // 非 Base64：按原始 JSON 文本解析
                return parseParams(req.bodyBase64());
            }
        }
        return m;
    }

    /** 参数 JSON 文本 → 扁平「参数名 → 字符串值」映射（空/非法一律返回空表）。 */
    private Map<String, String> parseParams(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            m.forEach((k, v) -> out.put(k, v == null ? null : String.valueOf(v)));
        } catch (Exception e) {
            log.warn("[integration] 解析参数快照失败: {}", e.toString());
        }
        return out;
    }

    static String sha256(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (IllegalArgumentException e) {
            // 非 Base64 时按原始 UTF-8 处理
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(base64.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String fmt(Instant t) {
        return t == null ? null : LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private HoldView toHoldView(RmsHold h) {
        String deviceCode = h.getDeviceId() == null ? null
                : deviceRepository.findById(h.getDeviceId()).map(Device::getCode).orElse(null);
        return new HoldView(h.getId(), h.getDeviceId(), deviceCode, h.getRecipeId(), h.getRecipeCode(),
                h.getReason(), h.getSource(), h.isActive(), h.getReleasedBy(), fmt(h.getReleasedAt()),
                h.getReleaseReason(), fmt(h.getCreateTime()));
    }

    private AlarmView toAlarmView(RmsAlarm a) {
        return new AlarmView(a.getId(), a.getAlarmCode(), a.getSeverity(), a.getDeviceId(), a.getLotId(),
                a.getRecipeCode(), a.getMessage(), a.getCorrelationId(), a.isAcknowledged(),
                fmt(a.getOccurredAt()));
    }

    // ==================== 视图 / 输入 ====================

    public record UploadReq(String toolId, String ppid, String recipeUid, String bodyFormat,
                            String bodyBase64, String bodyHash, String uploadReason, String opId,
                            String correlationId, String paramSnapshot) {
    }

    public record UploadResult(String result, String recipeUid, Integer version, String bodyHash,
                               List<ParamSpecService.SpecCheck> paramCheckReport) {
    }

    public record DownloadReq(String toolId, String ppidOnTool, String recipeUid, Integer version,
                              String downloadMode, String batchId, String lotId, String requestor,
                              String correlationId) {
    }

    public record DownloadResult(String correlationId, String stage, String result, String recipeUid,
                                 Integer version, String bodyBase64, String toolAck, String errorDesc,
                                 String batchId) {
    }

    public record OnlineCompareReq(String toolId, String lotId, String ppidOnTool, String recipeUid,
                                   String bodyFormat, String bodyBase64, String bodyHash, String cmpMode,
                                   String baselineType, String baselineVersionId, String correlationId,
                                   String paramSnapshot) {
    }

    public record OnlineCompareResult(String correlationId, String cmpResult, String recipeUid,
                                      Integer baselineVersion, List<ParamSpecService.SpecCheck> diffSummary,
                                      boolean holdApplied, boolean alarmRaised, long durationMs,
                                      String detail) {
    }

    public record RunCheckReq(String lotId, String productId, String processOperation, String toolId,
                              String recipeUid, String ppidOnTool, String versionPolicy, String exactVersionId,
                              Boolean verifyRequired, String requestor, String correlationId) {
    }

    public record RunCheckResult(String checkResult, String recipeUid, Integer activeVersion,
                                 String reason, String resultAt) {
    }

    public record HoldView(String id, String deviceId, String deviceCode, String recipeId, String recipeCode,
                           String reason, String source, boolean active, String releasedBy,
                           String releasedAt, String releaseReason, String createTime) {
    }

    public record AlarmView(String id, String alarmCode, String severity, String deviceId, String lotId,
                            String recipeCode, String message, String correlationId,
                            boolean acknowledged, String occurredAt) {
    }

    // ---------- 批量操作（W9） ----------

    /** 批量 Hold 请求：{@code action}=APPLY(缺省)/RELEASE；设备与配方至少给设备。 */
    public record BatchHoldReq(String action, List<String> deviceIds, List<String> deviceTypeIds,
                               List<String> recipeIds, String reason, String source) {
    }

    /** 批量下发请求：逐条目标显式列出（可预测性优先）；downloadMode/batchId/requestor 批次共用。 */
    public record BatchDownloadReq(String downloadMode, String batchId, String requestor,
                                   List<BatchDownloadItem> items) {
    }

    public record BatchDownloadItem(String toolId, String recipeUid, Integer version,
                                    String ppidOnTool, String lotId) {
    }

    /** 批量操作逐项结果（key = 设备码，target = 配方码/ALL）。 */
    public record BatchItem(String key, String target, boolean ok, String message) {
    }

    public record BatchOpResult(int total, int ok, int failed, List<BatchItem> items) {
    }

    // 供其它域复用的常量
    public static final Map<String, String> ALARM_CODES = Map.of(
            "RMS_COMPARE_FAIL", "比对失败",
            "RMS_DRIFT", "配方漂移",
            "RMS_HOLD", "配方 Hold",
            "RMS_NOT_QUALIFIED", "资格不符",
            "RMS_ECSV_FAIL", "EC/SV 校验失败",
            "RMS_VERIF_FAIL", "机台校验失败",
            "RMS_ORPHAN_PPID", "未纳管配方");
}
