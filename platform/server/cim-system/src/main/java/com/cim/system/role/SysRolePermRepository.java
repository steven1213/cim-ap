package com.cim.system.role;

import com.cim.jpa.support.BaseRepository;

import java.util.Collection;
import java.util.List;

/** 角色—权限关联仓储。 */
public interface SysRolePermRepository extends BaseRepository<SysRolePerm, String> {

    /** 某角色已授予的权限关联。 */
    List<SysRolePerm> findByRoleId(String roleId);

    /** 多个角色已授予的权限关联（权限解析用，避免 N+1）。 */
    List<SysRolePerm> findByRoleIdIn(Collection<String> roleIds);

    /** 清除某角色的全部权限授予（重新授权前调用）。 */
    void deleteByRoleId(String roleId);
}
