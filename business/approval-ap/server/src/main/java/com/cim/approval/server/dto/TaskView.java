package com.cim.approval.server.dto;

import java.time.Instant;
import java.util.List;

public record TaskView(
        String taskId,
        String processInstanceId,
        String processKey,
        String businessKey,
        String title,
        String tenantId,
        String name,
        String assignee,
        List<String> candidateUsers,
        String outcome,
        Instant createdAt,
        Instant dueDate,
        /** 超出超时阈值的分钟数（小数）；未超时为 null。 */
        Double overdueMinutes,
        /** Flowable 委派状态：PENDING=受托人待归还；历史/普通任务为 null。 */
        String delegationState,
        /** 当前轮次（第几轮审批；退回重提后递增），发起为 1。 */
        Integer round
) {
}
