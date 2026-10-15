package com.cim.rms.server.notification;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 站内通知（W9 审批实时通知，Req FR-L2「实时通知」缺口）。
 *
 * <p><b>收件模型</b>：二选一——
 * {@code recipient} 指定到人（如签核决定通知发起人）；
 * {@code audience} 广播到「角色能力组」（如 {@code APPROVER}=有签核审批权的人、
 * {@code INTEGRATION}=有集成操作权的人），可见性由服务端按当前用户权限实时过滤，
 * 不做人员快照——避免「先授权后通知」漏人。</p>
 *
 * <p><b>已读模型</b>：广播类通知被 A 已读不应影响 B，已读状态放
 * {@link RmsNotificationRead}（per-user），本表只存通知本体。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_notification")
public class RmsNotification extends BaseDefData {

    /** 通知类型：SIGNOFF_SUBMITTED / SIGNOFF_APPROVED / SIGNOFF_REJECTED / HOLD_APPLIED / DRIFT 等。 */
    @Column(name = "type", length = 48, nullable = false)
    private String type;

    @Column(name = "title", length = 128, nullable = false)
    private String title;

    @Column(name = "content", length = 512)
    private String content;

    /** 指定收件人用户名（与 audience 二选一，可都空=全员广播）。 */
    @Column(name = "recipient", length = 64)
    private String recipient;

    /** 广播受众：APPROVER / INTEGRATION（可见性=持有对应权限码）。 */
    @Column(name = "audience", length = 32)
    private String audience;

    /** 引用类型（SIGNOFF / HOLD / DOWNLOAD ...），前端点击直达。 */
    @Column(name = "ref_type", length = 32)
    private String refType;

    /** 引用主键（签核单 id 等）。 */
    @Column(name = "ref_id", length = 64)
    private String refId;

    /** 发生时刻。 */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();
}
