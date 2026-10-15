package com.cim.approval.server.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 审批详情聚合视图：实例信息 + 业务表单数据 + 实时状态 + 审批历史 + 附件。
 */
public record ProcessDetailView(
        String processInstanceId,
        String businessKey,
        String processKey,
        String definitionName,
        String title,
        String initiator,
        String tenantId,
        /** ACTIVE | COMPLETED */
        String status,
        Instant startedAt,
        Instant endedAt,
        /** 业务表单数据（发起时提交的结构化字段，键值展示） */
        Map<String, Object> formData,
        /** 表单字段描述（发起时随 formData 传入；详情页据此渲染标签/分组/类型） */
        List<FormSchemaField> formSchema,
        /** 当前活动节点 BPMN id（仅运行中，已完成则为空） */
        List<String> activeNodes,
        /** 当前待办任务（仅运行中） */
        List<TaskView> activeTasks,
        /** 审批历史（哈希链审计记录） */
        List<AuditView> history,
        List<AttachmentView> attachments
) {
}
