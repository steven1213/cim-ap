package com.cim.approval.server.dto;

import java.time.Instant;

/**
 * 流程节点轨迹（详情页进度可视化）。
 *
 * <p>按 BPMN 定义中 userTask 的文档顺序输出全部审批节点，标注当前到达状态：
 * 前端据此渲染节点条——已完成/进行中节点亮起，未到达节点置灰。</p>
 *
 * @param nodeId      节点 BPMN id
 * @param name        节点名称
 * @param status      PENDING 未到达 | ACTIVE 进行中 | DONE 已完成 | SKIPPED 未经过（条件分支未走，流程已终态）
 * @param actor       最近一次办理人（仅 DONE；退回重走取最新一轮）
 * @param outcome     最近一次办理结果（APPROVE/REJECT，仅 DONE 且可取到时）
 * @param completedAt 最近一次完成时间（仅 DONE）
 */
public record FlowNodeTrace(
        String nodeId,
        String name,
        String status,
        String actor,
        String outcome,
        Instant completedAt
) {
    public static final String PENDING = "PENDING";
    public static final String ACTIVE = "ACTIVE";
    public static final String DONE = "DONE";
    public static final String SKIPPED = "SKIPPED";
}
