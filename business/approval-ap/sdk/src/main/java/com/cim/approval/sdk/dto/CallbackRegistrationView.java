package com.cim.approval.sdk.dto;

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
