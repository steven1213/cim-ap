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
 * 角色—菜单关联（{@code sys_role_menu}）——「按角色配置菜单可见性」（README §21.1）。
 *
 * <p>菜单（导航）与权限（API/按钮）**分离**：本表只决定「看得见哪些导航节点」，
 * 不决定「能不能调这个 API」——后者由 {@code sys_role_perm} + {@code @PreAuthorize} 决定。
 * 两者通过 {@code sys_menu.perm_code} 在前端约定上对齐，后端不复用同一张表。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "sys_role_menu",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_role_menu",
                columnNames = {"role_id", "menu_id"}))
public class SysRoleMenu extends Auditable {

    @Id
    @Column(name = "id", length = 64, updatable = false, nullable = false)
    private String id;

    /** 角色 ID。 */
    @Column(name = "role_id", length = 64, nullable = false)
    private String roleId;

    /** 菜单 ID。 */
    @Column(name = "menu_id", length = 64, nullable = false)
    private String menuId;

    public static SysRoleMenu of(String roleId, String menuId) {
        SysRoleMenu link = new SysRoleMenu();
        link.setRoleId(roleId);
        link.setMenuId(menuId);
        return link;
    }
}
