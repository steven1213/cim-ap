package com.cim.system.permission;

import com.cim.core.history.History;
import com.cim.core.history.HistoryStrategy;
import com.cim.core.model.BaseDefData;
import com.cim.system.support.EnableStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.SQLRestriction;

/**
 * 权限（design.md §2.10）：权限码 {@code module:res:action}（README §21.1）。
 *
 * <p>粒度覆盖<b>后端 API 与按钮</b>——这是与「仅菜单权限」做法的关键差异：前端隐藏按钮
 * 不能替代后端校验，故每个受保护端点都对应一个权限码，由
 * {@code @PreAuthorize("hasAuthority('sys:user:list')")} 消费。</p>
 *
 * <p>{@code module}/{@code res}/{@code action} 三段由 {@link #code} 冗余拆分存储，
 * 便于「按模块/资源检索与批量授权」，同时保留 {@code code} 作为稳定契约。
 * 列名用 {@code res} 而非 {@code resource}——后者是 Oracle 保留字。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "sys_permission",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_permission_code_tenant",
                columnNames = {"code", "tenant_id", "deleted"}))
@History(HistoryStrategy.SNAPSHOT)
public class SysPermission extends BaseDefData {

    /** 权限码 {@code module:res:action}，稳定契约。 */
    @Column(name = "code", length = 128, nullable = false)
    private String code;

    /** 模块段（如 {@code sys}）。 */
    @Column(name = "module", length = 64)
    private String module;

    /** 资源段（如 {@code user}）。 */
    @Column(name = "res", length = 64)
    private String res;

    /** 动作段（如 {@code list} / {@code save}）。 */
    @Column(name = "action", length = 32)
    private String action;

    /** 名称（面向管理员）。 */
    @Column(name = "name", length = 128)
    private String name;

    /** 状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EnableStatus status = EnableStatus.ENABLED;

    /**
     * 由权限码拆解三段并赋值（保存前调用，保证三段与 {@code code} 不漂移）。
     *
     * @param code 形如 {@code sys:user:list}
     */
    public void applyCode(String code) {
        this.code = code;
        if (code == null) {
            return;
        }
        String[] parts = code.split(":");
        this.module = parts.length > 0 ? parts[0] : null;
        this.res = parts.length > 1 ? parts[1] : null;
        this.action = parts.length > 2 ? parts[2] : null;
    }
}
