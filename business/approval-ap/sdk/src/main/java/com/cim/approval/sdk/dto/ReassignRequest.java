package com.cim.approval.sdk.dto;

public record ReassignRequest(
        String newAssignee,
        String reason,
        String actor
) {
}
