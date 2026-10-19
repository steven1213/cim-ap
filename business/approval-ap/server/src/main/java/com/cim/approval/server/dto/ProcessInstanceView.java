package com.cim.approval.server.dto;

import java.time.Instant;
import java.util.List;

public record ProcessInstanceView(
        String processInstanceId,
        String businessKey,
        String processKey,
        String title,
        String tenantId,
        String status,
        Instant startedAt,
        Instant endedAt,
        /** 实例当前待办的最大超时分钟数（小数，仅 ACTIVE 且超时时非空）。 */
        Double overdueMinutes,
        /** 当前轮次（退回重提后递增）。 */
        Integer round,
        /** 是否等待发起人「修改并重新提交」（退回到发起人后的重提态）。 */
        Boolean awaitingResubmit,
        /** 业务摘要字段（G5）：我发起的列表副标题/卡片摘要。 */
        List<DisplayField> displayFields
) {
}
