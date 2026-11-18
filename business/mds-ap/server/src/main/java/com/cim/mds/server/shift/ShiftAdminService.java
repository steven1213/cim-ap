package com.cim.mds.server.shift;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 班次/班制定义维护服务（V17）——定义类主数据的 CRUD + 引用守门。
 *
 * <p>守门规则：{@code shift_code} / {@code pattern_code} 建后不可改；被班制、排班
 * 或交接记录引用的班次<b>禁删</b>（2004，只能停用）；班制 detail 项数必须等于
 * {@code cycle_days}，引用的班次必须存在且启用；被排班引用的班制禁删。</p>
 */
@Service
public class ShiftAdminService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final CimShiftDefRepository shiftRepository;
    private final CimShiftPatternDefRepository patternRepository;
    private final CimShiftAssignmentRepository assignmentRepository;
    private final CimShiftLogRepository logRepository;

    public ShiftAdminService(CimShiftDefRepository shiftRepository,
            CimShiftPatternDefRepository patternRepository,
            CimShiftAssignmentRepository assignmentRepository,
            CimShiftLogRepository logRepository) {
        this.shiftRepository = shiftRepository;
        this.patternRepository = patternRepository;
        this.assignmentRepository = assignmentRepository;
        this.logRepository = logRepository;
    }

    // ================================================================ 视图 / 请求

    /** 班次行。 */
    public record ShiftView(String id, String shiftCode, String shiftName, String startTime,
            String endTime, boolean crossDay, String color, boolean enabled, int sortNo,
            String description) {
    }

    /** 班次保存请求。 */
    public record ShiftReq(String shiftCode, String shiftName, String startTime, String endTime,
            boolean crossDay, String color, boolean enabled, int sortNo, String description) {
    }

    /** 班制行（detail 拆成数组，前端直接渲染行下拉）。 */
    public record PatternView(String id, String patternCode, String patternName, int cycleDays,
            List<String> detail, boolean enabled, int sortNo, String description) {
    }

    /** 班制保存请求。 */
    public record PatternReq(String patternCode, String patternName, int cycleDays,
            List<String> detail, boolean enabled, int sortNo, String description) {
    }

    // ================================================================ 班次

    /** 班次全量（sort_no 升序）。 */
    @Transactional(readOnly = true)
    public List<ShiftView> listShifts() {
        return shiftRepository.findByDeletedFalseOrderBySortNoAscShiftCodeAsc().stream()
                .map(ShiftAdminService::toView)
                .toList();
    }

    /** 启用班次（排班/铺排下拉口径）。 */
    @Transactional(readOnly = true)
    public List<ShiftView> listEnabledShifts() {
        return shiftRepository.findByEnabledTrueAndDeletedFalseOrderBySortNoAscShiftCodeAsc().stream()
                .map(ShiftAdminService::toView)
                .toList();
    }

    @Transactional
    public ShiftView createShift(ShiftReq req) {
        validateTime(req);
        if (shiftRepository.existsByShiftCodeAndDeletedFalse(req.shiftCode())) {
            throw new BizException(BizCode.DATA_DUPLICATE, "班次编码已存在: " + req.shiftCode());
        }
        CimShiftDef e = new CimShiftDef();
        applyShift(e, req);
        return toView(shiftRepository.save(e));
    }

    @Transactional
    public ShiftView updateShift(String id, ShiftReq req) {
        CimShiftDef e = shiftRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "班次不存在: " + id));
        if (!e.getShiftCode().equals(req.shiftCode())) {
            throw new BizException(BizCode.PARAM_INVALID, "班次编码建后不可改: " + e.getShiftCode());
        }
        validateTime(req);
        applyShift(e, req);
        return toView(shiftRepository.save(e));
    }

    /** 删除（被班制/排班/交接记录引用禁删 2004）。 */
    @Transactional
    public void deleteShift(String id) {
        CimShiftDef e = shiftRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "班次不存在: " + id));
        boolean referenced = patternRepository.findByDeletedFalseOrderBySortNoAscPatternCodeAsc().stream()
                .anyMatch(p -> p.getPatternDetail().contains(e.getShiftCode()))
                || assignmentRepository.existsByShiftCodeAndDeletedFalse(e.getShiftCode())
                || logRepository.findByDeletedFalseOrderByWorkDateDescCreateTimeDesc().stream()
                        .anyMatch(l -> l.getShiftCode().equals(e.getShiftCode()));
        if (referenced) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "班次已被班制/排班/交接记录引用，只能停用: " + e.getShiftCode());
        }
        e.setDeleted(true);
        shiftRepository.save(e);
    }

    // ================================================================ 班制

    /** 班制全量（detail 拆数组）。 */
    @Transactional(readOnly = true)
    public List<PatternView> listPatterns() {
        return patternRepository.findByDeletedFalseOrderBySortNoAscPatternCodeAsc().stream()
                .map(ShiftAdminService::toView)
                .toList();
    }

    /** 启用班制（铺排入口下拉口径）。 */
    @Transactional(readOnly = true)
    public List<PatternView> listEnabledPatterns() {
        return patternRepository.findByEnabledTrueAndDeletedFalseOrderBySortNoAscPatternCodeAsc().stream()
                .map(ShiftAdminService::toView)
                .toList();
    }

    @Transactional
    public PatternView createPattern(PatternReq req) {
        validateDetail(req, null);
        if (patternRepository.existsByPatternCodeAndDeletedFalse(req.patternCode())) {
            throw new BizException(BizCode.DATA_DUPLICATE, "班制编码已存在: " + req.patternCode());
        }
        CimShiftPatternDef e = new CimShiftPatternDef();
        applyPattern(e, req);
        return toView(patternRepository.save(e));
    }

    @Transactional
    public PatternView updatePattern(String id, PatternReq req) {
        CimShiftPatternDef e = patternRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "班制不存在: " + id));
        if (!e.getPatternCode().equals(req.patternCode())) {
            throw new BizException(BizCode.PARAM_INVALID, "班制编码建后不可改: " + e.getPatternCode());
        }
        validateDetail(req, e);
        applyPattern(e, req);
        return toView(patternRepository.save(e));
    }

    /** 删除（被排班引用禁删——班制是排班来源的事实依据；判据=其引用的班次有 PATTERN/COPY 来源排班）。 */
    @Transactional
    public void deletePattern(String id) {
        CimShiftPatternDef e = patternRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "班制不存在: " + id));
        List<String> detailCodes = List.of(e.getPatternDetail().split(","));
        boolean generated = assignmentRepository.findByWorkDateBetweenAndDeletedFalseOrderByWorkDateAscUserIdAsc(
                        LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31)).stream()
                .anyMatch(a -> ("PATTERN".equals(a.getSource()) || "COPY".equals(a.getSource()))
                        && detailCodes.contains(a.getShiftCode()));
        if (generated) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "班制已生成过排班，只能停用: " + e.getPatternCode());
        }
        e.setDeleted(true);
        patternRepository.save(e);
    }

    // ================================================================ 内部

    private void validateTime(ShiftReq req) {
        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(req.startTime(), HHMM);
            end = LocalTime.parse(req.endTime(), HHMM);
        } catch (Exception ex) {
            throw new BizException(BizCode.PARAM_INVALID, "时刻格式须为 HH:mm: "
                    + req.startTime() + " / " + req.endTime());
        }
        // 跨天允许 start > end（22:00-06:00）；不跨天必须 start < end（相等无意义）
        if (!req.crossDay() && !start.isBefore(end)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "非跨天班次要求开始早于结束（跨天请勾 cross_day）: " + req.startTime() + "-" + req.endTime());
        }
        if (req.crossDay() && !start.isAfter(end)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "跨天班次要求开始晚于结束: " + req.startTime() + "-" + req.endTime());
        }
    }

    /** detail 校验：项数 == cycle_days 且引用的班次存在且启用。 */
    private void validateDetail(PatternReq req, CimShiftPatternDef existing) {
        if (req.cycleDays() < 1 || req.cycleDays() > 31) {
            throw new BizException(BizCode.PARAM_INVALID, "周期天数须在 1-31 之间: " + req.cycleDays());
        }
        if (req.detail() == null || req.detail().size() != req.cycleDays()) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "轮转序列项数(" + (req.detail() == null ? 0 : req.detail().size())
                    + ")必须等于周期天数(" + req.cycleDays() + ")");
        }
        Map<String, CimShiftDef> byCode = shiftRepository.findByDeletedFalseOrderBySortNoAscShiftCodeAsc()
                .stream().collect(Collectors.toMap(CimShiftDef::getShiftCode, Function.identity()));
        for (String code : req.detail()) {
            CimShiftDef def = byCode.get(code);
            if (def == null) {
                throw new BizException(BizCode.PARAM_INVALID, "轮转序列引用了不存在的班次: " + code);
            }
            // 编辑本班制时，被本班制引用的班次允许处于停用态（历史兼容）；新建必须全启用
            if (existing == null && !Boolean.TRUE.equals(def.getEnabled())) {
                throw new BizException(BizCode.PARAM_INVALID, "轮转序列引用了停用班次: " + code);
            }
        }
    }

    private void applyShift(CimShiftDef e, ShiftReq req) {
        e.setShiftCode(req.shiftCode());
        e.setShiftName(req.shiftName());
        e.setStartTime(req.startTime());
        e.setEndTime(req.endTime());
        e.setCrossDay(req.crossDay());
        e.setColor(req.color());
        e.setEnabled(req.enabled());
        e.setSortNo(req.sortNo());
        e.setDescription(req.description());
    }

    private void applyPattern(CimShiftPatternDef e, PatternReq req) {
        e.setPatternCode(req.patternCode());
        e.setPatternName(req.patternName());
        e.setCycleDays(req.cycleDays());
        e.setPatternDetail(String.join(",", req.detail()));
        e.setEnabled(req.enabled());
        e.setSortNo(req.sortNo());
        e.setDescription(req.description());
    }

    private static ShiftView toView(CimShiftDef e) {
        return new ShiftView(e.getId(), e.getShiftCode(), e.getShiftName(), e.getStartTime(),
                e.getEndTime(), Boolean.TRUE.equals(e.getCrossDay()), e.getColor(),
                Boolean.TRUE.equals(e.getEnabled()), e.getSortNo(), e.getDescription());
    }

    private static PatternView toView(CimShiftPatternDef e) {
        List<String> detail = new ArrayList<>(List.of(e.getPatternDetail().split(",")));
        return new PatternView(e.getId(), e.getPatternCode(), e.getPatternName(), e.getCycleDays(),
                detail, Boolean.TRUE.equals(e.getEnabled()), e.getSortNo(), e.getDescription());
    }
}
