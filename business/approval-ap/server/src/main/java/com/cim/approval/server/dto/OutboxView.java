package com.cim.approval.server.dto;

import java.time.LocalDateTime;

public record OutboxView(
        String id,
        String messageId,
        String appCode,
        String businessKey,
        String eventType,
        String status,
        int attempts,
        LocalDateTime createTime,
        LocalDateTime sentAt,
        String lastError
) {
}
