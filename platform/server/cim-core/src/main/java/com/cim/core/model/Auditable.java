package com.cim.core.model;

import com.cim.core.event.EntityLifecycleCallbacks;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一审计字段基类（语义清晰，刻意不叫 {@code EventData} 以免与「事件」混淆）。
 *
 * <p>所有业务实体继承本类以获得一致的审计维度；字段由 {@code cim-jpa-starter} 的
 * {@code AuditableEntityListener} 在 {@code @PrePersist}/{@code @PreUpdate} 时自动填充，
 * 操作人取自 {@link com.cim.core.port.CurrentUserPort}（实现对接 SecurityContext），
 * 租户取自 {@link com.cim.core.port.TenantPort}。</p>
 *
 * <p>时间类型统一 {@link LocalDateTime}（对齐开发规范，弃用 {@link java.util.Date}）。</p>
 *
 * @see com.cim.core.model.BaseDefData
 * @see com.cim.core.model.BaseHistoryData
 */
@Getter
@Setter
@MappedSuperclass
public abstract class Auditable implements Serializable {

    /** 创建时间（首次落库，不再变更）。 */
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /** 事件时间（每次变更刷新）。 */
    @Column(name = "event_time")
    private LocalDateTime eventTime;

    /** 创建人。 */
    @Column(name = "create_user", length = 64, updatable = false)
    private String createUser;

    /** 事件人（最后操作人）。 */
    @Column(name = "event_user", length = 64)
    private String eventUser;

    /** 事件名（业务动作标识）。 */
    @Column(name = "event_name", length = 128)
    private String eventName;

    /** 事件备注。 */
    @Column(name = "event_comment", length = 512)
    private String eventComment;

    /** 事务号（与历史记录同源，便于按事务追溯）。 */
    @Column(name = "trx_id", length = 64)
    private String trxId;

    /** 租户 ID（与多租户 §10 自动填充打通）。 */
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    /** JPA 回调：插入前交给基础设施实现填充（主键 / 审计 / 租户）。 */
    @PrePersist
    protected void cimOnPrePersist() {
        EntityLifecycleCallbacks.beforeInsert(this);
    }

    /** JPA 回调：更新前交给基础设施实现填充（审计 / 租户）。 */
    @PreUpdate
    protected void cimOnPreUpdate() {
        EntityLifecycleCallbacks.beforeUpdate(this);
    }
}
