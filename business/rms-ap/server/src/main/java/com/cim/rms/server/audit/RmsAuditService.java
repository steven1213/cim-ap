package com.cim.rms.server.audit;

import com.cim.auth.principal.CimUserPrincipal;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 操作审计服务：业务动作落 {@code rms_audit_log}，供审计页（Req 17/18/19、FR-H1~H3）查询。
 *
 * <p>写入口 {@link #record} 由各业务服务在状态变更后调用（同事务），保证「业务成功才留痕」。
 * 查询支持多维过滤（类型/结果/操作人/时间范围/关键字）、汇总统计（FR-H2）与<b>配方对象历史</b>
 * 查询（FR-H3）。操作人从认证上下文（{@link CimUserPrincipal}）取，测试关认证时为空。</p>
 */
@Service
@RequiredArgsConstructor
public class RmsAuditService {

    private final RmsAuditLogRepository repository;

    /** 记录一条成功动作（写入口）。 */
    public void success(String type, String target, String detail) {
        record(type, "SUCCESS", target, detail);
    }

    /** 记录一条动作（指定结果）。 */
    @Transactional
    public void record(String type, String result, String target, String detail) {
        RmsAuditLog log = new RmsAuditLog();
        log.setType(type);
        log.setResult(result == null ? "SUCCESS" : result);
        log.setActor(currentActor());
        log.setTarget(target);
        log.setDetail(detail);
        repository.save(log);
    }

    /** 多维过滤查询（Req 19：时间范围 / 最大笔数可设）。 */
    public List<AuditView> list(AuditQuery q) {
        String type = blankToNull(q.type());
        String result = blankToNull(q.result());
        String actor = blankToNull(q.actor());
        String keyword = q.keyword() == null ? null : q.keyword().trim().toLowerCase(Locale.ROOT);
        Instant from = parseInstant(q.from());
        Instant to = parseInstant(q.to());
        ZoneId zone = ZoneId.systemDefault();

        List<RmsAuditLog> rows = type == null
                ? repository.findByDeletedFalseOrderByCreateTimeDesc()
                : repository.findByDeletedFalseAndTypeOrderByCreateTimeDesc(type);

        List<AuditView> out = new ArrayList<>();
        for (RmsAuditLog l : rows) {
            if (result != null && !result.equalsIgnoreCase(l.getResult())) {
                continue;
            }
            if (actor != null && !actor.equalsIgnoreCase(l.getActor())) {
                continue;
            }
            Instant t = l.getCreateTime() == null ? null : l.getCreateTime().atZone(zone).toInstant();
            if (from != null && (t == null || t.isBefore(from))) {
                continue;
            }
            if (to != null && (t == null || t.isAfter(to))) {
                continue;
            }
            if (keyword != null) {
                String hay = ((l.getActor() == null ? "" : l.getActor()) + " "
                        + (l.getTarget() == null ? "" : l.getTarget()) + " "
                        + (l.getDetail() == null ? "" : l.getDetail())).toLowerCase(Locale.ROOT);
                if (!hay.contains(keyword)) {
                    continue;
                }
            }
            out.add(toView(l));
        }
        int cap = q.limit() <= 0 ? 200 : Math.min(q.limit(), 1000);
        return out.stream().limit(cap).toList();
    }

    /** 兼容旧签名（仅按类型过滤 + 截断）。 */
    public List<AuditView> list(String type, int limit) {
        return list(new AuditQuery(type, null, null, null, null, null, limit));
    }

    /** 汇总统计（FR-H2）：总量 / 成功 / 失败 / 今日，以及 Top 动作类型分布。 */
    public AuditSummary summary() {
        List<RmsAuditLog> all = repository.findByDeletedFalseOrderByCreateTimeDesc();
        int success = 0, failure = 0, today = 0;
        LocalDate d0 = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (RmsAuditLog l : all) {
            if ("FAILURE".equalsIgnoreCase(l.getResult())) {
                failure++;
            } else {
                success++;
            }
            if (l.getCreateTime() != null
                    && l.getCreateTime().atZone(zone).toLocalDate().equals(d0)) {
                today++;
            }
            byType.merge(l.getType(), 1, Integer::sum);
        }
        List<TypeCount> top = byType.entrySet().stream()
                .map(e -> new TypeCount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(TypeCount::count).reversed())
                .limit(8)
                .toList();
        return new AuditSummary(all.size(), success, failure, today, top);
    }

    /** 配方对象历史查询（FR-H3/Req 19）：按业务键（配方编码）在时间范围内追溯。 */
    public List<AuditView> recipeHistory(String code, String from, String to, int max) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        String key = code.trim().toLowerCase(Locale.ROOT);
        Instant f = parseInstant(from);
        Instant t = parseInstant(to);
        ZoneId zone = ZoneId.systemDefault();
        int cap = max <= 0 ? 100 : Math.min(max, 500);
        List<AuditView> out = new ArrayList<>();
        for (RmsAuditLog l : repository.findByDeletedFalseOrderByCreateTimeDesc()) {
            String target = l.getTarget() == null ? "" : l.getTarget().toLowerCase(Locale.ROOT);
            if (!target.contains(key)) {
                continue;
            }
            Instant ti = l.getCreateTime() == null ? null : l.getCreateTime().atZone(zone).toInstant();
            if (f != null && (ti == null || ti.isBefore(f))) {
                continue;
            }
            if (t != null && (ti == null || ti.isAfter(t))) {
                continue;
            }
            out.add(toView(l));
            if (out.size() >= cap) {
                break;
            }
        }
        return out;
    }

    /** 当前操作人用户名（无认证上下文时返回 {@code null}）。 */
    public static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CimUserPrincipal p) {
            return p.username();
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        try {
            return Instant.parse(t);
        } catch (Exception ignore) {
            // 尝试本地日期时间
        }
        try {
            return LocalDateTime.parse(t).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignore) {
            // 尝试仅日期
        }
        try {
            return LocalDate.parse(t).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static AuditView toView(RmsAuditLog log) {
        return new AuditView(log.getId(), log.getType(), log.getResult(), log.getActor(),
                log.getTarget(), log.getDetail(), fmt(log.getCreateTime()));
    }

    // ==================== 视图 ====================

    /** 审计行传输对象（前端直接展示，时间已格式化）。 */
    public record AuditView(String id, String type, String result, String actor,
                            String target, String detail, String createTime) {
    }

    /** 多维查询参数。 */
    public record AuditQuery(String type, String result, String actor,
                             String from, String to, String keyword, int limit) {
    }

    public record TypeCount(String type, int count) {
    }

    public record AuditSummary(int total, int success, int failure, int today, List<TypeCount> topTypes) {
    }
}
