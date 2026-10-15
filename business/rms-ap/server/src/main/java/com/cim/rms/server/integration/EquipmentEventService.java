package com.cim.rms.server.integration;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.recipe.Recipe;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * GEM 设备事件服务（SEMI E30 Collection Event → RMS 处置）。
 *
 * <p><b>它补齐了什么</b>：原实现的漂移检测只有「主机定期轮询」（FR-C6）。行业内更基本的做法是
 * <b>设备主动上报</b>——GEM 明确要求「配方在机台上发生变化时设备必须通知主机」。没有这一路，
 * 工程师在机台改完配方，主机要等到下一轮巡检才知道，期间跑的就是「仓库存一份、机台上另一份」的货。</p>
 *
 * <p>本服务把两类事件落成业务动作：</p>
 * <ul>
 *   <li>{@code PP_CHANGE} —— 机台侧配方被 建/改/删。已纳管配方的「改/删」= 漂移信号 →
 *       报 {@code RMS_DRIFT} + 默认挂 Hold（可用 {@code holdOnDrift=false} 抑制，如保养窗口）；
 *       未纳管 PPID = 孤儿信号 → 报 {@code RMS_ORPHAN_PPID}（MINOR），提示核对是否需纳入管控。</li>
 *   <li>{@code PP_VERIFICATION_FAILED} —— 机台校验下发的配方内容失败（{@code PPError}）。
 *       行业铁律「校验失败的配方不得使用」→ 报警 {@code RMS_VERIF_FAIL}，并把在途下发任务判 {@code FAILED}，
 *       使下发阶段机与机台真实结论一致（否则任务会一直悬在 {@code TOOL_ACKED} 等一个永不到来的 {@code VERIFIED}）。</li>
 * </ul>
 *
 * <p>幂等：{@code idemKey} 唯一，EAP 断线重连重放同一事件不会重复报警/重复挂 Hold（Req §5.2）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentEventService {

    private final RmsEquipmentEventRepository eventRepository;
    private final DeviceRepository deviceRepository;
    private final ToolRecipeMapService toolRecipeMapService;
    private final RecipeDownloadJobRepository jobRepository;
    private final RecipeDownloadService downloadService;
    private final IntegrationService integration;
    private final RmsAuditService audit;
    private final ObjectMapper objectMapper;

    /**
     * 受理一个设备事件（幂等）。
     *
     * <p>同 {@code eventId}（或缺省时同「设备+类型+PPID+变更类型+发生时间」）重复上报直接回放，
     * 不重复报警、不重复挂 Hold。</p>
     */
    @Transactional
    public EventView handle(EquipmentEventReq req) {
        Device device = resolveDevice(req.toolId());
        EquipmentEventType type = parseType(req.eventType());
        String idem = idemKey(req, type);
        RmsEquipmentEvent existing = eventRepository.findFirstByIdemKeyAndDeletedFalse(idem).orElse(null);
        if (existing != null) {
            log.info("[gem-evt] 重复事件回放 idemKey={} result={}", idem, existing.getHandlingResult());
            return toView(existing);
        }
        RmsEquipmentEvent e = new RmsEquipmentEvent();
        e.setIdemKey(idem);
        e.setToolId(device.getId());
        e.setEventType(type);
        e.setPpid(blankToNull(req.ppid()));
        e.setChangeType(parseChangeType(req.changeType()));
        e.setOpId(blankToNull(req.opId()));
        e.setCorrelationId(blankToNull(req.correlationId()));
        e.setPayload(req.payload());
        e.setOccurredAt(req.occurredAt() == null ? Instant.now() : req.occurredAt());

        if (type == EquipmentEventType.PP_CHANGE) {
            handleChange(e, device, req);
        } else {
            handleVerificationFailed(e, device, req);
        }
        e.setHandled(true);
        RmsEquipmentEvent saved = eventRepository.save(e);
        audit.success("GEM_EVENT_" + type.name(), device.getCode(), saved.getHandlingResult());
        return toView(saved);
    }

    // ==================== PP_CHANGE ====================

    private void handleChange(RmsEquipmentEvent e, Device device, EquipmentEventReq req) {
        if (e.getPpid() == null) {
            e.setHandlingResult("PPID 缺失，仅留档（无法定位配方）");
            return;
        }
        if (e.getChangeType() == null) {
            // changeType 缺失（GEM 报文字段不全）：只留档，不臆断漂移（避免误挂 Hold 阻断生产）
            e.setHandlingResult("变更类型缺失（应为 CREATED/EDITED/DELETED），仅留档");
            return;
        }
        Recipe mapped = toolRecipeMapService.resolve(device.getId(), device.getDeviceTypeId(), e.getPpid());
        if (mapped == null) {
            // 机台出现 RMS 不认识的配方 → 合规信号：有人绕过了变更管控
            integration.raiseAlarm("RMS_ORPHAN_PPID", "MINOR", device.getId(), null, e.getPpid(),
                    "机台侧出现未纳管配方变更：PPID=" + e.getPpid() + "（" + e.getChangeType() + "）",
                    e.getCorrelationId());
            e.setAlarmRaised(true);
            e.setHandlingResult("未纳管 PPID（" + e.getChangeType() + "）→ 已报 RMS_ORPHAN_PPID，"
                    + "请核对是否需纳入 RMS 管控并建立 PPID 映射");
            return;
        }
        if (e.getChangeType() == EquipmentChangeType.CREATED) {
            // 已纳管配方被重建：不必然漂移，等回读/巡检核对即可，不报警以免噪声
            e.setHandlingResult("机台重建已纳管配方 " + mapped.getCode() + " → 待回读核对（未报警）");
            return;
        }
        // EDITED / DELETED：仓库与机台必然不再一致 → 漂移处置
        boolean deleted = e.getChangeType() == EquipmentChangeType.DELETED;
        e.setDriftDetected(true);
        integration.raiseAlarm("RMS_DRIFT", "MAJOR", device.getId(), null, mapped.getCode(),
                (deleted ? "机台侧删除已纳管配方 " : "机台侧编辑已纳管配方 ") + mapped.getCode()
                        + "（PPID=" + e.getPpid() + "），仓库与机台已不一致，需回读重同步",
                e.getCorrelationId());
        e.setAlarmRaised(true);
        boolean hold = !Boolean.FALSE.equals(req.holdOnDrift());
        if (hold) {
            integration.applyHold(device.getId(), mapped.getId(), mapped.getCode(),
                    "GEM PPChange 漂移信号（" + e.getChangeType() + "，PPID=" + e.getPpid() + "）", "GEM_EVENT");
            e.setHoldApplied(true);
        }
        e.setHandlingResult("漂移：机台" + e.getChangeType() + "已纳管配方 " + mapped.getCode()
                + "，已报 RMS_DRIFT" + (hold ? " 并挂 Hold" : "（未挂 Hold）"));
    }

    // ==================== PP_VERIFICATION_FAILED ====================

    private void handleVerificationFailed(RmsEquipmentEvent e, Device device, EquipmentEventReq req) {
        String ppid = e.getPpid();
        String ppError = extractError(req);
        integration.raiseAlarm("RMS_VERIF_FAIL", "MAJOR", device.getId(), null, ppid,
                "机台校验下发配方失败：PPID=" + ppid + (ppError == null ? "" : " · PPError=" + ppError),
                e.getCorrelationId());
        e.setAlarmRaised(true);

        // 联动：把该机台该 PPID 的在途下发任务判 FAILED，避免任务永悬 TOOL_ACKED
        int failed = 0;
        if (ppid != null) {
            List<RecipeDownloadJob> jobs = jobRepository
                    .findByDeviceIdAndPpidOnToolAndDeletedFalseOrderByCreateTimeDesc(device.getId(), ppid);
            for (RecipeDownloadJob j : jobs) {
                // 先判合法性再调用：非终态都可迁 FAILED，故不会抛异常污染当前事务
                if (!j.getStage().terminal() && j.getStage().canMoveTo(DownloadStage.FAILED)) {
                    downloadService.advance(j.getCorrelationId(), DownloadStage.FAILED, null,
                            "机台校验失败(PPVerificationFailed)"
                                    + (ppError == null ? "" : "：" + ppError), null);
                    failed++;
                }
            }
        }
        e.setHandlingResult("机台校验失败，已报 RMS_VERIF_FAIL；联动把在途下发任务判 FAILED "
                + failed + " 条（校验失败的配方不得使用）");
    }

    // ==================== 查询 ====================

    /** 事件查询（可按设备/类型过滤）。 */
    @Transactional(readOnly = true)
    public List<EventView> list(String toolId, String eventType) {
        EquipmentEventType t = parseTypeOrNull(eventType);
        List<RmsEquipmentEvent> rows;
        if (toolId != null && !toolId.isBlank() && t != null) {
            rows = eventRepository.findByToolIdAndEventTypeAndDeletedFalseOrderByCreateTimeDesc(toolId, t);
        } else if (toolId != null && !toolId.isBlank()) {
            rows = eventRepository.findByToolIdAndDeletedFalseOrderByCreateTimeDesc(toolId);
        } else if (t != null) {
            rows = eventRepository.findByEventTypeAndDeletedFalseOrderByCreateTimeDesc(t);
        } else {
            rows = eventRepository.findByDeletedFalseOrderByCreateTimeDesc();
        }
        return rows.stream().map(this::toView).toList();
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

    private static EquipmentEventType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "eventType 不能为空（PP_CHANGE / PP_VERIFICATION_FAILED）");
        }
        return parseTypeOrNull(raw);
    }

    private static EquipmentEventType parseTypeOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EquipmentEventType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(BizCode.PARAM_INVALID, "未知 eventType: " + raw
                    + "（可选 PP_CHANGE / PP_VERIFICATION_FAILED）");
        }
    }

    private static EquipmentChangeType parseChangeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return EquipmentChangeType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(BizCode.PARAM_INVALID, "未知 changeType: " + raw
                    + "（可选 CREATED / EDITED / DELETED）");
        }
    }

    /** 幂等键：优先 EAP 的 eventId，缺省时以内容哈希合成。 */
    private static String idemKey(EquipmentEventReq req, EquipmentEventType type) {
        String k = (req.eventId() != null && !req.eventId().isBlank())
                ? "EVT:" + req.eventId().trim()
                : "EVT:" + IntegrationIdempotencyService.contentKey(req.toolId(), type.name(),
                        req.ppid(), req.changeType(), req.correlationId(),
                        req.occurredAt() == null ? null : req.occurredAt().toString()).substring(2);
        return k.length() > 200 ? k.substring(0, 200) : k;
    }

    /** 从载荷中提取机台校验失败明细（PPError 语义）。 */
    private String extractError(EquipmentEventReq req) {
        if (req.errorDesc() != null && !req.errorDesc().isBlank()) {
            return req.errorDesc().trim();
        }
        String p = req.payload();
        if (p == null || p.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(p);
            for (String key : new String[]{"ppError", "PPError", "error", "message"}) {
                if (n.hasNonNull(key)) {
                    return n.get(key).asText();
                }
            }
        } catch (Exception ignore) {
            // 非 JSON：按原文摘录
        }
        return p.length() > 200 ? p.substring(0, 200) : p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String fmt(Instant t) {
        return t == null ? null : LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private EventView toView(RmsEquipmentEvent e) {
        String deviceCode = e.getToolId() == null ? null
                : deviceRepository.findById(e.getToolId()).map(Device::getCode).orElse(null);
        return new EventView(e.getId(), e.getToolId(), deviceCode,
                e.getEventType() == null ? null : e.getEventType().name(),
                e.getPpid(), e.getChangeType() == null ? null : e.getChangeType().name(),
                e.getOpId(), e.getCorrelationId(), fmt(e.getOccurredAt()), e.isHandled(),
                e.getHandlingResult(), e.isDriftDetected(), e.isAlarmRaised(), e.isHoldApplied(),
                fmt(e.getCreateTime()));
    }

    // ==================== 输入 / 视图 ====================

    /**
     * 设备事件请求（对齐 GEM Collection Event 的数据变量命名习惯）。
     *
     * @param eventId       EAP 侧事件唯一 id（幂等键；缺省时由内容哈希合成）
     * @param toolId        机台（id 或 code）
     * @param eventType     {@code PP_CHANGE} / {@code PP_VERIFICATION_FAILED}
     * @param ppid          GEM {@code PPChangeName}（变更的配方名）
     * @param changeType    GEM {@code PPChangeStatus}：{@code CREATED}/{@code EDITED}/{@code DELETED}
     * @param opId          机台侧操作人（可选）
     * @param correlationId 关联 id（回执/排障用）
     * @param payload       原始事件载荷（JSON，含 {@code PPError} 明细）
     * @param errorDesc     机台校验失败描述（{@code PPError} 快捷字段，替代 payload）
     * @param occurredAt    设备侧发生时间
     * @param holdOnDrift   漂移时是否挂 Hold（默认 true；保养窗口可传 false）
     */
    public record EquipmentEventReq(String eventId, String toolId, String eventType, String ppid,
                                    String changeType, String opId, String correlationId,
                                    String payload, String errorDesc, Instant occurredAt,
                                    Boolean holdOnDrift) {
    }

    /** 事件视图。 */
    public record EventView(String id, String toolId, String deviceCode, String eventType, String ppid,
                            String changeType, String opId, String correlationId, String occurredAt,
                            boolean handled, String handlingResult, boolean driftDetected,
                            boolean alarmRaised, boolean holdApplied, String createTime) {
    }
}
