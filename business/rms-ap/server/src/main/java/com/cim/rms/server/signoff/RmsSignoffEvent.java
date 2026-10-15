package com.cim.rms.server.signoff;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 签核事件时间线（Req.md FR-L4「全程留痕」、IF-W1「电子签证据链」）。
 *
 * <p>一条签核单的全生命周期事件：发起 / 参数差异生成 / 预设限时使用 / 取消预设 / 超时回落 /
 * 通过 / 拒绝。事件不可变更（append-only），与 {@link RecipeSignoff} 主体一起构成签核证据链，
 * 供详情抽屉的时间线呈现与审计追溯。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_signoff_event")
public class RmsSignoffEvent extends BaseDefData {

    /** 所属签核单（{@link RecipeSignoff} id）。 */
    @Column(name = "signoff_id", length = 64, nullable = false)
    private String signoffId;

    /** 事件类型（SUBMITTED / PARAM_DIFF / TEMP_ALLOWED / TEMP_CANCELLED / TEMP_EXPIRED / APPROVED / REJECTED）。 */
    @Column(name = "event_type", length = 32, nullable = false)
    private String eventType;

    /** 事件操作人。 */
    @Column(name = "actor", length = 64)
    private String actor;

    /** 备注/意见。 */
    @Column(name = "comment", length = 512)
    private String comment;

    /** 明细摘要（人类可读）。 */
    @Column(name = "detail", length = 512)
    private String detail;

    /** 事件发生时刻。 */
    @Column(name = "occurred_at")
    private Instant occurredAt;
}
