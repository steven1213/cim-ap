package com.cim.approval.server.dto;

import java.util.List;

/**
 * 退回目标选项（前端退回弹窗数据源）。
 *
 * @param previousNodeId   上一用户任务节点（默认目标）
 * @param previousNodeName 上一节点名称
 * @param initiatorNodeId   首节点 id（退到发起人=重提模式的目标）
 * @param initiatorNodeName 首节点名称
 * @param initiatorNodeMulti 首节点是否为多人节点（会签类）——true 时不提供退到发起人
 * @param historicNodes     已审批过的用户节点（不含首节点，供「指定节点」选择）
 * @param currentRound      当前轮次
 * @param maxRounds         退回上限
 * @param parallelActive    实例当前是否处于并行段（并行段内退回目标受限，BR-05）
 */
public record ReturnTargetsView(
        String previousNodeId,
        String previousNodeName,
        String initiatorNodeId,
        String initiatorNodeName,
        boolean initiatorNodeMulti,
        List<NodeOption> historicNodes,
        int currentRound,
        int maxRounds,
        boolean parallelActive
) {
    public record NodeOption(String id, String name) {
    }
}
