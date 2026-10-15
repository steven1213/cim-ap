package com.cim.approval.sdk.dto;

import java.time.Instant;
import java.util.List;

public record TaskView(
        String taskId,
        String processInstanceId,
        String processKey,
        String businessKey,
        String tenantId,
        String name,
        String assignee,
        List<String> candidateUsers,
        String outcome,
        Instant createdAt,
        Instant dueDate
) {
}
