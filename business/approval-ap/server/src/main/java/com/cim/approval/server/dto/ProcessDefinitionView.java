package com.cim.approval.server.dto;

public record ProcessDefinitionView(
        String id,
        String key,
        String name,
        int version,
        String tenantId,
        /** 停用后不可发起新实例。 */
        boolean suspended
) {
}
