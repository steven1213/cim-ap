package com.cim.approval.server.dto;

import java.time.Instant;

public record CallbackRegistrationView(
        String id,
        String appCode,
        String baseUrl,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
