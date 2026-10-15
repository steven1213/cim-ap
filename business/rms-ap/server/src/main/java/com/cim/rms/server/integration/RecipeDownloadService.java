package com.cim.rms.server.integration;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * IF-E2 配方下发任务服务：阶段机 + 回执闭环（Req §5.3、Req 21/40）。
 *
 * <p><b>为什么需要它</b>：下发是异步长流程（机台可能忙碌，Req P6），原实现只在请求内返回一次
 * {@code QUEUED} 就结束——EAP 拿到 Body 之后没有任何地方推进状态，也无法证明「机台真的装上了」。
 * 本服务把 {@code RecipeDownloadProgress} 落成可跟踪任务：</p>
 *
 * <ol>
 *   <li>{@link #open} —— 下发受理，落任务（{@code QUEUED}；同 correlationId 重试复用同一任务）；</li>
 *   <li>{@link #advance} —— EAP 逐步回执 {@code GRANTED → SENT_TO_TOOL → TOOL_ACKED}，
 *       每次校验<b>合法迁移</b>（乱序/跳阶直接拒绝，避免状态被写坏）；</li>
 *   <li>{@code VERIFIED} —— 携带回读 HASH 时做 S7F5/F6 语义的比对：一致才置 {@code VERIFIED}，
 *       不一致直接判 {@code FAILED} 并报 {@code RMS_DRIFT} 报警（配方漂移），形成闭环证据。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeDownloadService {

    private final RecipeDownloadJobRepository jobRepository;
    private final RmsAlarmRepository alarmRepository;
    private final com.cim.rms.server.recipe.RecipeRepository recipeRepository;
    private final RmsAuditService audit;

    /**
     * 受理一次下发，落任务。
     *
     * <p>幂等：同 {@code correlationId} 已存在则复用（EAP 重试不会产生第二个任务）。</p>
     */
    @Transactional
    public JobView open(String correlationId, String deviceId, String recipeId, String versionId,
                        String ppidOnTool, String downloadMode, String batchId, String lotId,
                        String requestor, String bodyHash) {
        RecipeDownloadJob existing = jobRepository
                .findFirstByCorrelationIdAndDeletedFalse(correlationId).orElse(null);
        if (existing != null) {
            log.info("[download] 重复下发受理，复用任务 correlationId={} stage={}", correlationId, existing.getStage());
            return toView(existing);
        }
        RecipeDownloadJob j = new RecipeDownloadJob();
        j.setCorrelationId(correlationId);
        j.setDeviceId(deviceId);
        j.setRecipeId(recipeId);
        j.setVersionId(versionId);
        j.setPpidOnTool(ppidOnTool);
        j.setBatchId(batchId);
        j.setLotId(lotId);
        j.setRequestor(requestor);
        j.setDownloadMode(downloadMode == null || downloadMode.isBlank()
                ? "IMMEDIATE" : downloadMode.toUpperCase(Locale.ROOT));
        j.setStage(DownloadStage.QUEUED);
        j.setBodyHash(bodyHash);
        RecipeDownloadJob saved = jobRepository.save(j);
        audit.success("DOWNLOAD_QUEUED", null,
                "下发受理 correlationId=" + correlationId + " · " + saved.getDownloadMode()
                        + (batchId == null ? "" : " · batch=" + batchId));
        return toView(saved);
    }

    /**
     * 推进下发阶段（EAP 回执）。
     *
     * @param correlationId 任务幂等键
     * @param target        目标阶段（{@code QUEUED} 表示失败后重新入队）
     * @param toolAck       机台确认码（S7F4 ACKC7，可空）
     * @param errorDesc     失败原因（目标为 {@code FAILED} 时必填，强制留痕）
     * @param readbackHash  机台回读的 Body SHA-256（S7F5/F6；仅 {@code VERIFIED} 有意义）
     */
    @Transactional
    public JobView advance(String correlationId, DownloadStage target, String toolAck,
                           String errorDesc, String readbackHash) {
        RecipeDownloadJob j = jobRepository.findFirstByCorrelationIdAndDeletedFalse(correlationId)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "下发任务不存在: " + correlationId));
        if (target == null) {
            throw new BizException(BizCode.PARAM_INVALID, "stage 不能为空");
        }
        // 变更前的阶段（日志/审计要打印"从哪来"，必须在 setStage 之前取）
        DownloadStage from = j.getStage();
        if (!from.canMoveTo(target)) {
            throw new BizException(BizCode.PARAM_INVALID, "非法阶段迁移: " + from + " → " + target
                    + "（当前允许: " + from.nextAllowed() + "）");
        }
        if (target == DownloadStage.FAILED && (errorDesc == null || errorDesc.isBlank())) {
            throw new BizException(BizCode.PARAM_INVALID, "失败必须填写原因（强制留痕）");
        }
        if (toolAck != null && !toolAck.isBlank()) {
            j.setToolAck(toolAck.toUpperCase(Locale.ROOT));
        }

        // VERIFIED：有回读 HASH 则做 S7F5/F6 比对，不一致判漂移
        if (target == DownloadStage.VERIFIED
                && readbackHash != null && !readbackHash.isBlank()
                && j.getBodyHash() != null && !j.getBodyHash().isBlank()
                && !readbackHash.equalsIgnoreCase(j.getBodyHash())) {
            j.setStage(DownloadStage.FAILED);
            j.setVerified(false);
            j.setErrorDesc("回读 HASH 不匹配（期望 " + shortHash(j.getBodyHash())
                    + " 实得 " + shortHash(readbackHash) + "）");
            RecipeDownloadJob saved = jobRepository.save(j);
            raiseDriftAlarm(j, "机台回读 Body 与下发不一致，疑似配方漂移");
            audit.record("DOWNLOAD_VERIFY", "FAILURE", null,
                    "回读不符 correlationId=" + correlationId + " device=" + j.getDeviceId());
            return toView(saved);
        }

        j.setStage(target);
        if (target == DownloadStage.VERIFIED) {
            j.setVerified(true);
            j.setErrorDesc(null);
        } else if (target == DownloadStage.FAILED) {
            j.setVerified(false);
            j.setErrorDesc(errorDesc);
        } else if (target == DownloadStage.QUEUED) {
            // 失败重试入队：清掉上次的失败痕迹
            j.setErrorDesc(null);
            j.setVerified(false);
        }
        RecipeDownloadJob saved = jobRepository.save(j);
        audit.success("DOWNLOAD_" + target.name(), null,
                "下发阶段 → " + target + " correlationId=" + correlationId
                        + (saved.getToolAck() == null ? "" : " · ack=" + saved.getToolAck())
                        + (errorDesc == null || errorDesc.isBlank() ? "" : " · " + errorDesc));
        log.info("[download] 阶段推进 correlationId={} {} → {}", correlationId, from, target);
        return toView(saved);
    }

    /** 下发任务查询（可按阶段/批次/设备过滤；{@code pendingOnly} 只留未闭环）。 */
    @Transactional(readOnly = true)
    public List<JobView> list(DownloadStage stage, String batchId, String deviceId, boolean pendingOnly) {
        List<RecipeDownloadJob> rows;
        if (batchId != null && !batchId.isBlank()) {
            rows = jobRepository.findByBatchIdAndDeletedFalseOrderByCreateTimeDesc(batchId);
        } else if (deviceId != null && !deviceId.isBlank()) {
            rows = jobRepository.findByDeviceIdAndDeletedFalseOrderByCreateTimeDesc(deviceId);
        } else if (stage != null) {
            rows = jobRepository.findByStageAndDeletedFalseOrderByCreateTimeDesc(stage);
        } else {
            rows = jobRepository.findByDeletedFalseOrderByCreateTimeDesc();
        }
        return rows.stream()
                .filter(r -> !pendingOnly || !r.getStage().terminal())
                .filter(r -> stage == null || r.getStage() == stage)
                .map(this::toView)
                .toList();
    }

    /** 批量下发进度汇总（Req 40：批量逐台回执聚合）。 */
    @Transactional(readOnly = true)
    public BatchProgress batchProgress(String batchId) {
        List<RecipeDownloadJob> rows = batchId == null || batchId.isBlank()
                ? List.of() : jobRepository.findByBatchIdAndDeletedFalseOrderByCreateTimeDesc(batchId);
        int verified = 0, failed = 0, pending = 0;
        for (RecipeDownloadJob r : rows) {
            switch (r.getStage()) {
                case VERIFIED -> verified++;
                case FAILED -> failed++;
                default -> pending++;
            }
        }
        return new BatchProgress(batchId, rows.size(), verified, failed, pending);
    }

    private void raiseDriftAlarm(RecipeDownloadJob j, String message) {
        // alarm.recipe_code 在其它域存的是「配方编码」而非 UID，这里保持一致（回查不到则退化为 UID）
        String code = j.getRecipeId() == null ? null
                : recipeRepository.findById(j.getRecipeId())
                        .map(com.cim.rms.server.recipe.Recipe::getCode).orElse(j.getRecipeId());
        RmsAlarm a = new RmsAlarm();
        a.setAlarmCode("RMS_DRIFT");
        a.setSeverity("MAJOR");
        a.setDeviceId(j.getDeviceId());
        a.setLotId(j.getLotId());
        a.setRecipeCode(code);
        a.setMessage(message);
        a.setCorrelationId(j.getCorrelationId());
        a.setOccurredAt(Instant.now());
        a.setAcknowledged(false);
        alarmRepository.save(a);
        log.warn("[download] 已报 RMS_DRIFT correlationId={}", j.getCorrelationId());
    }

    private static String shortHash(String h) {
        return h == null ? "—" : h.substring(0, Math.min(12, h.length()));
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private JobView toView(RecipeDownloadJob j) {
        return new JobView(j.getId(), j.getCorrelationId(), j.getDeviceId(), j.getRecipeId(),
                j.getVersionId(), j.getPpidOnTool(), j.getBatchId(), j.getLotId(), j.getRequestor(),
                j.getDownloadMode(), j.getStage().name(), j.getStage().terminal(), j.isVerified(),
                j.getToolAck(), j.getErrorDesc(), j.getBodyHash(), fmt(j.getCreateTime()));
    }

    /** 下发任务视图（对应 Req §5.3 {@code RecipeDownloadProgress}）。 */
    public record JobView(String id, String correlationId, String deviceId, String recipeId,
                          String versionId, String ppidOnTool, String batchId, String lotId,
                          String requestor, String downloadMode, String stage, boolean terminal,
                          boolean verified, String toolAck, String errorDesc, String bodyHash,
                          String createTime) {
    }

    /** 批量下发进度聚合。 */
    public record BatchProgress(String batchId, int total, int verified, int failed, int pending) {
    }
}
