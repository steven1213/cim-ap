package com.cim.approval.server.service;

import com.cim.approval.server.dto.StatsView;
import com.cim.approval.server.repository.ApprovalAuditRepository;
import com.cim.approval.server.security.SecurityGuard;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批效率统计：平均处理时长、驳回率、审批人负载、按日趋势。
 * 数据量可控（窗口期内全量取回内存聚合）；后续量大可改定时物化。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final HistoryService historyService;
    private final TaskService taskService;
    private final ApprovalAuditRepository auditRepository;
    private final SecurityGuard securityGuard;

    /** 超时阈值（Duration），与 OverdueScanner/列表标红共用同一配置。 */
    @org.springframework.beans.factory.annotation.Value("${approval.task.timeout:24h}")
    private Duration timeout;

    public StatsView stats(String tenantId, int days) {
        // G3：统计曾是唯一「传入任意 tenantId 即可跨域读」的端点——没有域断言，只有过滤条件。
        // 现在域只有一个来源，越域 403；这里的 domain 同时用于下面每一条查询。
        String domain = securityGuard.resolveDomain(tenantId);
        Instant since = Instant.now().minus(Duration.ofDays(days));

        // ---- 已完成实例：平均耗时 + 按日完成趋势 ----
        List<HistoricProcessInstance> finished = historyService.createHistoricProcessInstanceQuery()
                .variableValueEquals("tenantId", domain)
                .finished()
                .startedAfter(java.util.Date.from(since))
                .includeProcessVariables()
                .list();
        double avgHours = 0;
        if (!finished.isEmpty()) {
            double totalMillis = 0;
            for (HistoricProcessInstance hpi : finished) {
                Instant end = toInstant(hpi.getEndTime());
                totalMillis += Duration.between(toInstant(hpi.getStartTime()), end).toMillis();
            }
            avgHours = totalMillis / finished.size() / 3_600_000.0;
        }

        // ---- 发起趋势（窗口内全部实例，含未完成）----
        List<HistoricProcessInstance> all = historyService.createHistoricProcessInstanceQuery()
                .variableValueEquals("tenantId", domain)
                .startedAfter(java.util.Date.from(since))
                .includeProcessVariables()
                .list();
        Map<LocalDate, long[]> byDay = new HashMap<>();
        Set<String> finishedIds = new HashSet<>();
        for (HistoricProcessInstance hpi : finished) {
            finishedIds.add(hpi.getId());
        }
        for (HistoricProcessInstance hpi : all) {
            LocalDate day = toInstant(hpi.getStartTime()).atZone(ZoneId.systemDefault()).toLocalDate();
            long[] pair = byDay.computeIfAbsent(day, k -> new long[2]);
            pair[0]++;
            if (finishedIds.contains(hpi.getId())) {
                pair[1]++;
            }
        }

        // ---- 驳回率 ----
        long approveCount = auditRepository.countByActionAndAppCodeAndCreatedAtAfter("COMPLETE_APPROVE", domain, since);
        long rejectCount = auditRepository.countByActionAndAppCodeAndCreatedAtAfter("COMPLETE_REJECT", domain, since);
        Double rejectRate = (approveCount + rejectCount) > 0
                ? (double) rejectCount / (approveCount + rejectCount) : null;

        // ---- 审批人负载：窗口内已办票数 TOP + 当前待办数 ----
        Map<String, Long> doneByUser = new HashMap<>();
        historyService.createHistoricTaskInstanceQuery()
                .processVariableValueEquals("tenantId", domain)
                .finished()
                .taskCompletedAfter(java.util.Date.from(since))
                .list()
                .forEach(t -> {
                    if (t.getAssignee() != null) {
                        doneByUser.merge(t.getAssignee(), 1L, Long::sum);
                    }
                });
        Map<String, Long> todoByUser = new HashMap<>();
        taskService.createTaskQuery().active().processVariableValueEquals("tenantId", domain).list()
                .forEach(t -> {
                    if (t.getAssignee() != null) {
                        todoByUser.merge(t.getAssignee(), 1L, Long::sum);
                    }
                });
        List<StatsView.WorkloadRow> workloads = new ArrayList<>();
        doneByUser.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> workloads.add(new StatsView.WorkloadRow(
                        e.getKey(), e.getValue(), todoByUser.getOrDefault(e.getKey(), 0L))));

        // ---- 趋势序列（按日升序，缺失日补 0）----
        List<StatsView.TrendRow> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            long[] pair = byDay.getOrDefault(d, new long[2]);
            trend.add(new StatsView.TrendRow(d.toString(), pair[0], pair[1]));
        }

        long todoCount = taskService.createTaskQuery().active()
                .processVariableValueEquals("tenantId", domain).count();
        long overdueCount = taskService.createTaskQuery().active()
                .processVariableValueEquals("tenantId", domain)
                .taskCreatedBefore(java.util.Date.from(
                        Instant.now().minus(timeout)))
                .count();
        return new StatsView(todoCount, overdueCount, avgHours, rejectRate, workloads, trend);
    }

    private static Instant toInstant(java.util.Date d) {
        return d == null ? Instant.now() : d.toInstant();
    }
}
