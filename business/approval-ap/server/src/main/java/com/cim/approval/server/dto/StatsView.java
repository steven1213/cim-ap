package com.cim.approval.server.dto;

import java.util.List;

/** 审批效率统计（Dashboard 看板）。 */
public record StatsView(
        long todoCount,
        long overdueCount,
        /** 已完成实例平均耗时（小时）；无完成实例时为 null。 */
        Double avgCompleteHours,
        /** 驳回率：COMPLETE_REJECT /（APPROVE+REJECT）；无审批票时为 null。 */
        Double rejectRate,
        List<WorkloadRow> workloads,
        List<TrendRow> trend
) {
    /** 审批人负载：窗口期内已办票数 + 当前待办数。 */
    public record WorkloadRow(String assignee, long done, long todo) {
    }

    /** 按日趋势。 */
    public record TrendRow(String date, long started, long completed) {
    }
}
