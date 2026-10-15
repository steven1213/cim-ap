package com.cim.approval.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 不可篡改审计日志：顺序哈希链，覆盖审批全动作。
 *
 * <p>action 取值：START / COMPLETE_APPROVE / COMPLETE_REJECT / RETURN / ADD_SIGN /
 * REASSIGN / CALLBACK_SENT / CALLBACK_FAILED。哈希链由 {@code HashChainUtil} 计算。</p>
 */
@Entity
@Table(name = "approval_audit")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalAudit {

    @Id
    @Column(length = 40)
    private String id;

    /** 同一 businessKey 内的序号（从 1 递增），用于链条定位。 */
    @Column(name = "seq", nullable = false)
    private long seq;

    @Column(name = "business_key", length = 128)
    private String businessKey;

    @Column(name = "process_instance_id", length = 64)
    private String processInstanceId;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "before_state", length = 64)
    private String beforeState;

    @Column(name = "after_state", length = 64)
    private String afterState;

    @Lob
    @Column(name = "detail")
    private String detail;

    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @Column(name = "hash", nullable = false, length = 64)
    private String hash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
