package com.cim.approval.server.dto;

public record ProcessDefinitionView(
        String id,
        String key,
        String name,
        int version,
        String tenantId
) {
}
