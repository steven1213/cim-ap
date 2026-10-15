package com.cim.approval.server.service;

import com.cim.approval.server.dto.StatsView;
import com.cim.approval.server.repository.ApprovalAuditRepository;
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

    /** 超时阈值（分钟），与 OverdueScanner/列表标红共用同一配置。 */
    @org.springframework.beans.factory.annotation.Value("${approval.task.timeout-minutes:1440}")
    private double timeoutMinutes;

    public StatsView stats(String tenantId, int days) {
        Instant since = Instant.now().minus(Duration.ofDays(days));

        // ---- 已完成实例：平均耗时 + 按日完成趋势 ----
        List<HistoricProcessInstance> finished = historyService.createHistoricProcessInstanceQuery()
                .variableValueEquals("tenantId", tenantId)
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
                .variableValueEquals("tenantId", tenantId)
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
        long approveCount = auditRepository.countByActionAndCreatedAtAfter("COMPLETE_APPROVE", since);
        long rejectCount = auditRepository.countByActionAndCreatedAtAfter("COMPLETE_REJECT", since);
        Double rejectRate = (approveCount + rejectCount) > 0
                ? (double) rejectCount / (approveCount + rejectCount) : null;

        // ---- 审批人负载：窗口内已办票数 TOP + 当前待办数 ----
        Map<String, Long> doneByUser = new HashMap<>();
        historyService.createHistoricTaskInstanceQuery()
                .finished()
                .taskCompletedAfter(java.util.Date.from(since))
                .list()
                .forEach(t -> {
                    if (t.getAssignee() != null) {
                        doneByUser.merge(t.getAssignee(), 1L, Long::sum);
                    }
                });
        Map<String, Long> todoByUser = new HashMap<>();
        taskService.createTaskQuery().active().list()
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

        long todoCount = taskService.createTaskQuery().active().count();
        long overdueCount = taskService.createTaskQuery().active()
                .taskCreatedBefore(java.util.Date.from(
                        Instant.now().minus(Duration.ofMinutes((long) (timeoutMinutes * 60)))))
                .count();
        return new StatsView(todoCount, overdueCount, avgHours, rejectRate, workloads, trend);
    }

    private static Instant toInstant(java.util.Date d) {
        return d == null ? Instant.now() : d.toInstant();
    }
}
