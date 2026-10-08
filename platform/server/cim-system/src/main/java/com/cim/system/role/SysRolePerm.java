package com.cim.system.role;

import com.cim.core.model.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

/**
 * 角色—权限关联（{@code sys_role_perm}）。
 *
 * <p>把角色授予权限码（{@code module:res:action}）。权限码本身定义在
 * {@code sys_permission}，由 {@code SysPermission} 维护；本表只记录授予关系。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "sys_role_perm",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_role_perm",
                columnNames = {"role_id", "perm_id"}))
public class SysRolePerm extends Auditable {

    @Id
    @Column(name = "id", length = 64, updatable = false, nullable = false)
    private String id;

    /** 角色 ID。 */
    @Column(name = "role_id", length = 64, nullable = false)
    private String roleId;

    /** 权限 ID。 */
    @Column(name = "perm_id", length = 64, nullable = false)
    private String permId;

    public static SysRolePerm of(String roleId, String permId) {
        SysRolePerm link = new SysRolePerm();
        link.setRoleId(roleId);
        link.setPermId(permId);
        return link;
    }
}
