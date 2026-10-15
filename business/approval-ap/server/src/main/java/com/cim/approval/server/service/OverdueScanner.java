package com.cim.approval.server.service;

import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 超时任务扫描：定时检查运行中任务的停留时长，超过阈值（approval.task.timeout-minutes）
 * 向办理人/候选人推送 SSE 提醒（TASK_OVERDUE）。
 *
 * <p>重推策略：同任务每 {@code approval.task.renotify-hours}（默认 4h）重推一次，
 * 而非终身一次——持续超时的任务保持提醒压力。内存记录，重启后首轮会重推一次（可接受）；
 * 多实例部署应替换为分布式调度 + DB 去重表。与列表侧的超时标红互补：
 * 列表标红是「查看时可见」，本扫描是「不查看也送达」。</p>
 */
@Service
@RequiredArgsConstructor
public class OverdueScanner {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final NotificationService notificationService;

    /** 超时阈值（分钟，支持小数），与列表标红共用同一配置。 */
    @Value("${approval.task.timeout-minutes:1440}")
    private double timeoutMinutes;

    /** 同任务重推间隔（小时）。 */
    @Value("${approval.task.renotify-hours:4}")
    private double renotifyHours;

    /** 任务 → 最近推送时间（内存；重启后首轮重推一次，可接受）。 */
    private final Map<String, Instant> lastNotified = new ConcurrentHashMap<>();

    @Scheduled(
            initialDelayString = "${approval.task.scan-initial-delay-ms:60000}",
            fixedDelayString = "${approval.task.scan-interval-ms:300000}")
    public void scan() {
        Instant threshold = Instant.now().minus(Duration.ofMillis((long) (timeoutMinutes * 60_000)));
        Duration renotifyGap = Duration.ofMillis((long) (renotifyHours * 3_600_000));
        List<Task> overdue = taskService.createTaskQuery()
                .active()
                .taskCreatedBefore(Date.from(threshold))
                .list();
        for (Task t : overdue) {
            Instant last = lastNotified.get(t.getId());
            if (last != null && Duration.between(last, Instant.now()).compareTo(renotifyGap) < 0) {
                continue;
            }
            // 扫描间隙实例可能已结束
            if (runtimeService.createProcessInstanceQuery()
                    .processInstanceId(t.getProcessInstanceId()).count() == 0) {
                continue;
            }
            long overdueMinutes = Duration.between(toInstant(t.getCreateTime()), Instant.now()).toMinutes();
            Set<String> users = new HashSet<>();
            if (t.getAssignee() != null && !t.getAssignee().isBlank()) {
                users.add(t.getAssignee());
            }
            for (IdentityLink link : taskService.getIdentityLinksForTask(t.getId())) {
                if ("candidate".equals(link.getType()) && link.getUserId() != null) {
                    users.add(link.getUserId());
                }
            }
            for (String user : users) {
                notificationService.pushToUser(user, "TASK_OVERDUE", Map.of(
                        "type", "TASK_OVERDUE",
                        "taskId", t.getId(),
                        "name", t.getName(),
                        "processInstanceId", t.getProcessInstanceId(),
                        "overdueMinutes", overdueMinutes));
            }
            if (!users.isEmpty()) {
                lastNotified.put(t.getId(), Instant.now());
            }
        }
    }

    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
