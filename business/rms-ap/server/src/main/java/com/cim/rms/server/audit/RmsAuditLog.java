package com.cim.rms.server.audit;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * RMS 操作审计日志（Req.md FR-H1/H3、Req 12/19；NFR-5 敏感操作双留痕的业务侧落点）。
 *
 * <p>记录关键动作流水：谁（{@code actor}）在何时（{@code createTime}，由基类自动填充）
 * 对哪个对象（{@code target}）做了什么（{@code type} / {@code detail}），结果如何（{@code result}）。
 * 与 IAM 侧审计（{@code /admin/audit}）同构，但<b>只记本 ap 业务动作</b>，落<b>RMS 自己的库</b>。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_audit_log")
public class RmsAuditLog extends BaseDefData {

    /** 动作类型（如 RECIPE_CREATED / RECIPE_ACTIVATED / SIGNOFF_APPROVED / DEVICE_DELETED）。 */
    @Column(name = "type", length = 64, nullable = false)
    private String type;

    /** 结果（{@code SUCCESS} / {@code FAILURE}，与 IAM 审计对齐）。 */
    @Column(name = "result", length = 16, nullable = false)
    private String result = "SUCCESS";

    /** 操作人（用户名；取自认证上下文，登出/系统动作可为 {@code null}）。 */
    @Column(name = "actor", length = 64)
    private String actor;

    /** 操作对象（业务键，如配方编码、设备编码；便于按对象追溯）。 */
    @Column(name = "target", length = 128)
    private String target;

    /** 明细（人类可读摘要，如「v3 激活，旧 v2 转 OBSOLETE」）。 */
    @Column(name = "detail", length = 512)
    private String detail;
}
