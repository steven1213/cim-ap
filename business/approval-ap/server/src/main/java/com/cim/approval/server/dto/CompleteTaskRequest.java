package com.cim.approval.server.dto;

public record CompleteTaskRequest(
        /** APPROVE / REJECT */
        String outcome,
        String comment,
        String actor
) {
}
