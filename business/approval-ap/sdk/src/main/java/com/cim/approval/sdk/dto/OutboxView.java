package com.cim.approval.sdk.dto;

import java.time.Instant;

public record OutboxView(
        String id,
        String messageId,
        String appCode,
        String businessKey,
        String eventType,
        String status,
        int attempts,
        Instant createdAt,
        Instant sentAt,
        String lastError
) {
}
