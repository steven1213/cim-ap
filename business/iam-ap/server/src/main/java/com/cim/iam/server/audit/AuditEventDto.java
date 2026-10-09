package com.cim.iam.server.audit;

import java.time.LocalDateTime;

/**
 * 审计事件对外 DTO（避免直接暴露含审计列的实体）。
 */
public record AuditEventDto(
        String id,
        String type,
        String actor,
        String subject,
        String result,
        String detail,
        LocalDateTime createTime) {

    public static AuditEventDto of(AuditEvent e) {
        return new AuditEventDto(
                e.getId(), e.getType(), e.getActor(), e.getSubject(),
                e.getResult(), e.getDetail(), e.getCreateTime());
    }
}
