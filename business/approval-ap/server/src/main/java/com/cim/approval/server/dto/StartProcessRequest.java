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
        List<FormSchemaField> formSchema,
        /** 业务摘要字段（可选，G5）：卡片/列表/审计一眼可见的关键项；缺省由 formSchema.display:true 兜底 */
        List<DisplayField> displayFields,
        /**
         * 角色选人（G6 / D8）：节点 id → 角色 key 列表。
         * 设计器导出 BPMN 时每个角色型审批节点引用变量 {@code ccg_<nodeId>}，发起时本映射
         * 提供对应角色集合（如 RMS_APPROVER）。运行时任务据此建立候选组，待办按当前用户
         * JWT 的 roles claim 命中候选组。与 approvers/assignee/candidateUsers 互斥使用。
         */
        Map<String, List<String>> candidateGroups
) {
}
