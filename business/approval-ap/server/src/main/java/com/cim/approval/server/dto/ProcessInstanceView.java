package com.cim.approval.server.dto;

import java.time.Instant;

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
        Double overdueMinutes
) {
}
