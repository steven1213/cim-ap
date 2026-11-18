package com.cim.mds.server.shift;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排班安排服务（V17）——周视图查询、单格保存、班制铺排、复制上周。
 *
 * <p>交互口径（方案定稿）：铺排/复制<b>一步落库</b>，但 {@code source=MANUAL} 的
 * 人工格<b>永不被批量生成覆盖</b>（人工安排是当事人确认过的事实）；同一
 * {@code (work_date, user_id)} 唯一，单格保存=upsert（重排=改班，清空=软删）。</p>
 */
@Service
public class ShiftAssignmentService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CimShiftAssignmentRepository assignmentRepository;
    private final CimShiftDefRepository shiftRepository;
    private final CimShiftPatternDefRepository patternRepository;

    public ShiftAssignmentService(CimShiftAssignmentRepository assignmentRepository,
            CimShiftDefRepository shiftRepository,
            CimShiftPatternDefRepository patternRepository) {
        this.assignmentRepository = assignmentRepository;
        this.shiftRepository = shiftRepository;
        this.patternRepository = patternRepository;
    }

    // ================================================================ 视图 / 请求

    /** 排班格（周视图单元格）。 */
    public record AssignmentView(String id, String workDate, String userId, String username,
            String shiftCode, String source, Long version) {
    }

    /** 单格保存请求（shiftCode 空 = 清空该格）。 */
    public record CellReq(String workDate, String userId, String username, String shiftCode) {
    }

    /** 班制铺排请求：从起始日（周一）起铺 cycleDays × users。 */
    public record GenerateReq(String patternCode, String startDate, List<String> userIds,
            Map<String, String> usernames) {
    }

    /** 复制请求：把 fromWeek 起始（周一）的整周平移到 toWeek 起始。 */
    public record CopyReq(String fromWeekStart, String toWeekStart) {
    }

    /** 简历人员行（选人下拉；DirectoryClient 的 profiles 摘要）。 */
    public record UserOption(String userId, String username, boolean enabled) {
    }

    // ================================================================ 查询

    /** 周区间排班（from 周一起算 7 天）。 */
    @Transactional(readOnly = true)
    public List<AssignmentView> week(String fromIso) {
        LocalDate from = LocalDate.parse(fromIso, ISO);
        LocalDate to = from.plusDays(6);
        return assignmentRepository
                .findByWorkDateBetweenAndDeletedFalseOrderByWorkDateAscUserIdAsc(from, to).stream()
                .map(ShiftAssignmentService::toView)
                .toList();
    }

    // ================================================================ 单格保存

    /** 单格 upsert：shiftCode 空 = 清空（软删）；重排=改班；同格并发按唯一约束兜底。 */
    @Transactional
    public AssignmentView saveCell(CellReq req, String operator) {
        if (req.workDate() == null || req.userId() == null || req.userId().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "work_date 与 user_id 必填");
        }
        LocalDate date = LocalDate.parse(req.workDate(), ISO);
        CimShiftAssignment existing = assignmentRepository
                .findByWorkDateAndUserIdAndDeletedFalse(date, req.userId()).orElse(null);

        if (req.shiftCode() == null || req.shiftCode().isBlank()) {
            // 清空：无行则幂等返回，有行则软删
            if (existing == null) {
                return null;
            }
            existing.setDeleted(true);
            assignmentRepository.save(existing);
            return null;
        }

        if (!shiftRepository.findByShiftCodeAndDeletedFalse(req.shiftCode())
                .map(d -> Boolean.TRUE.equals(d.getEnabled()))
                .orElse(false)) {
            throw new BizException(BizCode.PARAM_INVALID, "班次不存在或已停用: " + req.shiftCode());
        }

        if (existing == null) {
            CimShiftAssignment e = new CimShiftAssignment();
            e.setWorkDate(date);
            e.setUserId(req.userId());
            e.setUsername(req.username() == null || req.username().isBlank() ? req.userId() : req.username());
            e.setShiftCode(req.shiftCode());
            e.setSource("MANUAL");
            return toView(assignmentRepository.save(e));
        }
        existing.setShiftCode(req.shiftCode());
        existing.setSource("MANUAL");
        if (req.username() != null && !req.username().isBlank()) {
            existing.setUsername(req.username());
        }
        return toView(assignmentRepository.save(existing));
    }

    // ================================================================ 班制铺排（一步落库）

    /**
     * 按 pattern 从起始日铺排 {@code detail[(dayIndex) % cycleDays]}；跳过 MANUAL 格；
     * 同格已有非人工排班则按新班次覆盖（PATTERN/COPY 可被重新生成）。
     *
     * @return 落库行数（新建 + 改班）
     */
    @Transactional
    public int generate(GenerateReq req, String operator) {
        CimShiftPatternDef pattern = patternRepository.findByPatternCodeAndDeletedFalse(req.patternCode())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "班制不存在: " + req.patternCode()));
        if (!Boolean.TRUE.equals(pattern.getEnabled())) {
            throw new BizException(BizCode.PARAM_INVALID, "班制已停用: " + req.patternCode());
        }
        List<String> detail = List.of(pattern.getPatternDetail().split(","));
        // detail 项数与 cycleDays 一致性在班制保存时已校验；铺排按 cycleDays 取模，防御式截断
        int cycle = Math.min(pattern.getCycleDays(), detail.size());
        if (req.userIds() == null || req.userIds().isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "铺排人员为空");
        }
        LocalDate start = LocalDate.parse(req.startDate(), ISO);

        int written = 0;
        for (String userId : req.userIds()) {
            for (int day = 0; day < cycle; day++) {
                LocalDate date = start.plusDays(day);
                String shiftCode = detail.get(day % cycle);
                CimShiftAssignment existing = assignmentRepository
                        .findByWorkDateAndUserIdAndDeletedFalse(date, userId).orElse(null);
                // 人工格保护：MANUAL 不被模板覆盖
                if (existing != null && "MANUAL".equals(existing.getSource())) {
                    continue;
                }
                if (existing == null) {
                    CimShiftAssignment e = new CimShiftAssignment();
                    e.setWorkDate(date);
                    e.setUserId(userId);
                    e.setUsername(usernameOf(req, userId));
                    e.setShiftCode(shiftCode);
                    e.setSource("PATTERN");
                    assignmentRepository.save(e);
                } else {
                    existing.setShiftCode(shiftCode);
                    existing.setSource("PATTERN");
                    assignmentRepository.save(existing);
                }
                written++;
            }
        }
        return written;
    }

    /** 复制整周：from 周一日起 7 天平移到 to 周一起；跳过 MANUAL 格。 */
    @Transactional
    public int copyWeek(CopyReq req) {
        LocalDate from = LocalDate.parse(req.fromWeekStart(), ISO);
        LocalDate to = LocalDate.parse(req.toWeekStart(), ISO);
        if (!from.getDayOfWeek().equals(DayOfWeek.MONDAY) || !to.getDayOfWeek().equals(DayOfWeek.MONDAY)) {
            throw new BizException(BizCode.PARAM_INVALID, "复制基准必须是周一");
        }
        List<CimShiftAssignment> source = assignmentRepository
                .findByWorkDateBetweenAndDeletedFalseOrderByWorkDateAscUserIdAsc(from, from.plusDays(6));
        Set<String> manualTargets = assignmentRepository
                .findByWorkDateBetweenAndDeletedFalseOrderByWorkDateAscUserIdAsc(to, to.plusDays(6)).stream()
                .filter(a -> "MANUAL".equals(a.getSource()))
                .map(a -> a.getWorkDate() + "|" + a.getUserId())
                .collect(Collectors.toSet());

        int written = 0;
        Set<String> seen = new HashSet<>();
        for (CimShiftAssignment s : source) {
            LocalDate targetDate = s.getWorkDate().plusDays(7);
            String key = targetDate + "|" + s.getUserId();
            if (manualTargets.contains(key) || !seen.add(key)) {
                continue;
            }
            CimShiftAssignment existing = assignmentRepository
                    .findByWorkDateAndUserIdAndDeletedFalse(targetDate, s.getUserId()).orElse(null);
            if (existing != null) {
                existing.setShiftCode(s.getShiftCode());
                existing.setSource("COPY");
                assignmentRepository.save(existing);
            } else {
                CimShiftAssignment e = new CimShiftAssignment();
                e.setWorkDate(targetDate);
                e.setUserId(s.getUserId());
                e.setUsername(s.getUsername());
                e.setShiftCode(s.getShiftCode());
                e.setSource("COPY");
                assignmentRepository.save(e);
            }
            written++;
        }
        return written;
    }

    /** 选人下拉（IAM 目录全量启用档案摘要）。 */
    @Transactional(readOnly = true)
    public List<UserOption> userOptions(List<Map<String, Object>> profiles) {
        List<UserOption> out = new ArrayList<>();
        for (Map<String, Object> p : profiles) {
            String userId = String.valueOf(p.getOrDefault("userId", ""));
            if (userId.isBlank()) {
                continue;
            }
            String name = (String) p.get("displayName");
            boolean enabled = !"INACTIVE".equals(p.get("status"));
            out.add(new UserOption(userId,
                    name == null || name.isBlank() ? userId : name, enabled));
        }
        return out;
    }

    private String usernameOf(GenerateReq req, String userId) {
        String name = req.usernames() == null ? null : req.usernames().get(userId);
        return name == null || name.isBlank() ? userId : name;
    }

    private static AssignmentView toView(CimShiftAssignment e) {
        return new AssignmentView(e.getId(), e.getWorkDate().toString(), e.getUserId(),
                e.getUsername(), e.getShiftCode(), e.getSource(), e.getVersion());
    }
}
