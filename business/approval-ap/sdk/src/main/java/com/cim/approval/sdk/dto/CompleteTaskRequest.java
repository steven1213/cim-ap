package com.cim.approval.sdk.dto;

/** APPROVE / REJECT */
public record CompleteTaskRequest(String outcome, String comment, String actor) {
}
