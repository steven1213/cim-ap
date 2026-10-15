package com.cim.approval.server.dto;

import java.time.Instant;

public record AuditView(
        String id,
        long seq,
        String businessKey,
        String processInstanceId,
        String taskId,
        String action,
        String actor,
        String beforeState,
        String afterState,
        String detail,
        String prevHash,
        String hash,
        Instant createdAt
) {
}
