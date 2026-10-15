package com.cim.approval.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 回调 Outbox：流程结束事件先落库，再由调度器可靠投递，保证最终一致。
 */
@Entity
@Table(name = "callback_outbox")
@Getter
@Setter
@NoArgsConstructor
public class CallbackOutbox {

    public enum Status {
        PENDING,   // 待投递
        SENT,      // 已成功
        FAILED,    // 投递失败待重试
        DEAD       // 超过最大重试次数，进入死信
    }

    @Id
    @Column(length = 40)
    private String id;

    /** 幂等键：业务方据此去重。 */
    @Column(name = "message_id", nullable = false, unique = true, length = 64)
    private String messageId;

    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    /** 业务编号（businessKey），便于对账。 */
    @Column(name = "business_key", length = 128)
    private String businessKey;

    /** 事件类型，如 PROCESS_COMPLETED。 */
    @Column(name = "event_type", length = 64)
    private String eventType;

    /** 待投递的回调载荷（JSON）。 */
    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 6;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}
