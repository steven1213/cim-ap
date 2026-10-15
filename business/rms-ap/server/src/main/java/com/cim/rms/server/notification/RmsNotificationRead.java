package com.cim.rms.server.notification;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 通知已读登记（per-user）：广播类通知被一人已读不影响他人。
 * 唯一约束 {@code (notification_id, username)} 保证幂等。
 */
@Getter
@Setter
@Entity
@Table(name = "rms_notification_read")
public class RmsNotificationRead extends BaseDefData {

    @Column(name = "notification_id", length = 64, nullable = false)
    private String notificationId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "read_at", nullable = false)
    private Instant readAt = Instant.now();
}
