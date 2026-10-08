package com.cim.system.role;

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
 * 角色——授权的单位（design.md §2.10 / README §21.1 三级授权「用户 → 角色 → 权限」）。
 *
 * <p>{@code is_super} 为超级管理员标记：命中即<b>短路放通</b>（{@code DbLocalAuthorityLoader}
 * 直接返回超管权限码，不再逐条展开权限），与 {@code CimPermissionEvaluator} 的
 * {@code SUPER_ADMIN}/{@code ROLE_SUPER} 短路保持一致。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@SQLRestriction("deleted = false")
@Table(name = "sys_role",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_role_code_tenant",
                columnNames = {"code", "tenant_id", "deleted"}))
@History(HistoryStrategy.SNAPSHOT)
public class SysRole extends BaseDefData {

    /** 角色编码（稳定契约，前端/后端按码引用）。 */
    @Column(name = "code", length = 64, nullable = false)
    private String code;

    /** 角色名称。 */
    @Column(name = "name", length = 128)
    private String name;

    /** 超级管理员标记：为 {@code true} 时该角色下用户短路放通。 */
    @Column(name = "is_super", nullable = false)
    private Boolean isSuper = Boolean.FALSE;

    /** 排序号。 */
    @Column(name = "sort_no")
    private Integer sortNo = 0;

    /** 状态。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EnableStatus status = EnableStatus.ENABLED;
}
