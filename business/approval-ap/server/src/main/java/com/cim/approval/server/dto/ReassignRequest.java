package com.cim.approval.server.dto;

public record ReassignRequest(
        String newAssignee,
        String reason,
        String actor
) {
}
