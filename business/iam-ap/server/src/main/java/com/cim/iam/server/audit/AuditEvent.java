package com.cim.iam.server.audit;

import com.cim.core.model.BaseDefData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 审计事件（IAM 管理面可观测性）。
 *
 * <p>记录「谁（actor）对谁（subject）做了什么类型（type）的事、结果如何（result）」。
 * 继承 {@link BaseDefData} 以获得统一审计列（create_time / create_user / tenant_id 等，由
 * {@code cim-jpa-starter} 的实体监听器自动填充）。</p>
 *
 * <p>与业务历史表（{@code @History}）不同，本表是**跨实体的动作流水**，用于排障与合规追溯，
 * 不做整行快照。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "audit_event")
public class AuditEvent extends BaseDefData {

    /** 事件类型（{@link AuditType} 名称）。 */
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    /** 操作人（发起该动作的用户标识；系统动作可为 {@code system} 或空）。 */
    @Column(name = "actor", length = 128)
    private String actor;

    /** 动作对象（被操作用户 / 应用接入码等）。 */
    @Column(name = "subject", length = 128)
    private String subject;

    /** 结果：{@code SUCCESS} / {@code FAILURE}。 */
    @Column(name = "result", length = 16)
    private String result;

    /** 补充说明（如角色组、失败原因）。 */
    @Column(name = "detail", length = 512)
    private String detail;

    /** 来源 IP（可选）。 */
    @Column(name = "client_ip", length = 64)
    private String clientIp;
}
