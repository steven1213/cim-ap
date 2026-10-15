package com.cim.approval.sdk.dto;

import java.time.Instant;

public record ProcessInstanceView(
        String processInstanceId,
        String businessKey,
        String processKey,
        String tenantId,
        String status,
        Instant startedAt,
        Instant endedAt
) {
}
