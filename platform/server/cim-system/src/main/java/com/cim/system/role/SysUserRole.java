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
 * 用户—角色关联（{@code sys_user_role}）。
 *
 * <p>多对多关系表：关系变更「先删后插」，无需历史表——授权的<b>变更轨迹</b>由
 * {@code sys_user_hist} / {@code sys_role_hist} 与实际权限码快照承载。</p>
 *
 * <p>主键为应用层时间有序 ID（由生命周期回调注入，见 README §3）；不继承 API 定义基类
 * 是因为关系表无 {@code version}/{@code deleted} 语义。</p>
 */
@Getter
@Setter
@Entity
@Filter(name = "cimTenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "sys_user_role",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sys_user_role",
                columnNames = {"user_id", "role_id"}))
public class SysUserRole extends Auditable {

    @Id
    @Column(name = "id", length = 64, updatable = false, nullable = false)
    private String id;

    /** 用户 ID。 */
    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    /** 角色 ID。 */
    @Column(name = "role_id", length = 64, nullable = false)
    private String roleId;

    public static SysUserRole of(String userId, String roleId) {
        SysUserRole link = new SysUserRole();
        link.setUserId(userId);
        link.setRoleId(roleId);
        return link;
    }
}
