package com.cim.approval.server.dto;

public record ReturnRequest(
        /** 目标活动节点 id；为空则自动跳回上一用户任务节点 */
        String targetActivityId,
        String reason,
        String actor
) {
}
