package com.cim.approval.sdk.dto;

public record ProcessDefinitionView(
        String id,
        String key,
        String name,
        int version,
        String tenantId
) {
}
