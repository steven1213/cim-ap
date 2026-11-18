package com.cim.mds.server.shift;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 交接班记录服务（V17）——责任文书生命周期：交班提交（UNACKED 可改）→
 * 接班签收（ACKED 锁定只读）。
 *
 * <p>签收走乐观锁（{@code version}）防并发重复签收；签收后任何字段不可改
 * （含交班人自己的修改权——签收即定稿）。{@code work_date} 归属按班次开始那天，
 * 由班次定义的时间段推算。</p>
 */
@Service
public class ShiftLogService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final CimShiftLogRepository logRepository;
    private final CimShiftDefRepository shiftRepository;

    public ShiftLogService(CimShiftLogRepository logRepository, CimShiftDefRepository shiftRepository) {
        this.logRepository = logRepository;
        this.shiftRepository = shiftRepository;
    }

    // ================================================================ 视图 / 请求

    /** 交接记录行。 */
    public record LogView(String id, String workDate, String shiftCode, String shiftName,
            String handoverUser, String handoverName, String receiverUser, String receiverName,
            String equipmentIssue, String pendingItems, String remarks,
            boolean acked, String ackBy, String ackName, String ackAt, Long version) {
    }

    /** 保存请求（workDate 空时按当前时间 + 班次时间段推算）。 */
    public record LogReq(String id, String workDate, String shiftCode,
            String receiverUser, String receiverName,
            String equipmentIssue, String pendingItems, String remarks) {
    }

    // ================================================================ 查询

    /** 全量（work_date 倒序，最新在前）。 */
    @Transactional(readOnly = true)
    public List<LogView> list(String shiftCode) {
        List<CimShiftLog> logs = (shiftCode == null || shiftCode.isBlank())
                ? logRepository.findByDeletedFalseOrderByWorkDateDescCreateTimeDesc()
                : logRepository.findByShiftCodeAndDeletedFalseOrderByWorkDateDescCreateTimeDesc(shiftCode);
        return logs.stream().map(this::toView).toList();
    }

    /** 详情。 */
    @Transactional(readOnly = true)
    public LogView get(String id) {
        return logRepository.findByIdAndDeletedFalse(id)
                .map(this::toView)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "交接记录不存在: " + id));
    }

    // ================================================================ 保存 / 签收

    /** 交班人提交或修改（仅 UNACKED 可改；workDate 空按当前时间推算归属）。 */
    @Transactional
    public LogView save(LogReq req, String operatorId, String operatorName) {
        CimShiftLog e;
        if (req.id() != null && !req.id().isBlank()) {
            e = logRepository.findByIdAndDeletedFalse(req.id())
                    .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "交接记录不存在: " + req.id()));
            if (Boolean.TRUE.equals(e.getAcked())) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "交接记录已签收锁定，不可修改");
            }
        } else {
            e = new CimShiftLog();
            e.setHandoverUser(operatorId);
            e.setHandoverName(operatorName);
        }
        e.setShiftCode(requireShift(req.shiftCode()));
        e.setWorkDate(resolveWorkDate(req.workDate(), req.shiftCode()));
        e.setReceiverUser(req.receiverUser());
        e.setReceiverName(req.receiverName());
        e.setEquipmentIssue(req.equipmentIssue());
        e.setPendingItems(req.pendingItems());
        e.setRemarks(req.remarks());
        return toView(logRepository.save(e));
    }

    /** 接班人签收（乐观锁；签收后锁定只读）。 */
    @Transactional
    public LogView ack(String id, Long version, String ackUserId, String ackUserName) {
        CimShiftLog e = logRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "交接记录不存在: " + id));
        if (Boolean.TRUE.equals(e.getAcked())) {
            return toView(e); // 幂等：已签收直接返回
        }
        if (version != null && !version.equals(e.getVersion())) {
            throw new BizException(BizCode.CONCURRENT_MODIFIED, "交接记录已被他人更新，请刷新后重试");
        }
        e.setAcked(true);
        e.setAckBy(ackUserId);
        e.setAckName(ackUserName);
        e.setAckAt(LocalDateTime.now());
        return toView(logRepository.save(e));
    }

    // ================================================================ 内部

    /** workDate 空时按「当前时刻落在哪个班次」推算（跨天班次按开始日记）。 */
    private LocalDate resolveWorkDate(String workDateIso, String shiftCode) {
        if (workDateIso != null && !workDateIso.isBlank()) {
            return LocalDate.parse(workDateIso);
        }
        CimShiftDef def = shiftRepository.findByShiftCodeAndDeletedFalse(shiftCode)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "班次不存在: " + shiftCode));
        LocalTime now = LocalTime.now();
        LocalTime start = LocalTime.parse(def.getStartTime(), HHMM);
        LocalTime end = LocalTime.parse(def.getEndTime(), HHMM);
        LocalDate today = LocalDate.now();
        boolean crossDay = Boolean.TRUE.equals(def.getCrossDay());
        boolean inShift = crossDay
                ? now.isAfter(start) || now.isBefore(end)      // 22:00-06:00：夜里两端都算
                : !now.isBefore(start) && now.isBefore(end);
        if (inShift) {
            // 跨天班次的后半夜（次日凌晨）仍归属开始那天
            return crossDay && now.isBefore(end) && now.isBefore(start) ? today.minusDays(1) : today;
        }
        return today;
    }

    private String requireShift(String shiftCode) {
        if (shiftCode == null || shiftCode.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "班次必填");
        }
        if (!shiftRepository.findByShiftCodeAndDeletedFalse(shiftCode)
                .map(d -> Boolean.TRUE.equals(d.getEnabled()))
                .orElse(false)) {
            throw new BizException(BizCode.PARAM_INVALID, "班次不存在或已停用: " + shiftCode);
        }
        return shiftCode;
    }

    private LogView toView(CimShiftLog e) {
        String shiftName = shiftRepository.findByShiftCodeAndDeletedFalse(e.getShiftCode())
                .map(CimShiftDef::getShiftName).orElse(e.getShiftCode());
        return new LogView(e.getId(), e.getWorkDate().toString(), e.getShiftCode(), shiftName,
                e.getHandoverUser(), e.getHandoverName(), e.getReceiverUser(), e.getReceiverName(),
                e.getEquipmentIssue(), e.getPendingItems(), e.getRemarks(),
                Boolean.TRUE.equals(e.getAcked()), e.getAckBy(), e.getAckName(),
                e.getAckAt() == null ? null : e.getAckAt().toString(), e.getVersion());
    }
}
