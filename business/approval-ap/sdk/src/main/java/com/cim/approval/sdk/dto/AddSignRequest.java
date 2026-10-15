package com.cim.approval.sdk.dto;

public record AddSignRequest(
        /** 被加签人 */
        String assignee,
        /** AFTER=后加签（追加在当前节点之后）；BEFORE=前加签（插入在当前节点之前） */
        String type,
        String reason,
        String actor
) {
}
