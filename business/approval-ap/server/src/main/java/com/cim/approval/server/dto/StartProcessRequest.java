package com.cim.approval.server.dto;

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
        Map<String, Object> variables,
        /** 结构化业务表单数据（审批内容主体，详情页键值展示） */
        Map<String, Object> formData,
        /** 表单字段描述（可选）：详情页据此渲染中文标签/分组/类型化值，键与 formData 对应 */
        List<FormSchemaField> formSchema
) {
}
