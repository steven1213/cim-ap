package com.cim.approval.sdk.dto;

import java.util.List;
import java.util.Map;

public record StartProcessRequest(
        String processKey,
        String businessKey,
        String tenantId,
        String initiator,
        String title,
        /** 会签场景：审批人列表 */
        List<String> approvers,
        /** 单级审批场景：指定审批人 */
        String assignee,
        /** 透传业务变量（如表单摘要），随流程实例保存 */
        Map<String, Object> variables
) {
}
