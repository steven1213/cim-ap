package com.cim.system.role;

import com.cim.jpa.support.BaseRepository;

import java.util.Collection;
import java.util.List;

/** 角色—菜单关联仓储（导航可见性）。 */
public interface SysRoleMenuRepository extends BaseRepository<SysRoleMenu, String> {

    /** 某角色已关联的菜单。 */
    List<SysRoleMenu> findByRoleId(String roleId);

    /** 多个角色已关联的菜单（菜单树解析用，避免 N+1）。 */
    List<SysRoleMenu> findByRoleIdIn(Collection<String> roleIds);

    /** 清除某角色的全部菜单关联（重新授权前调用）。 */
    void deleteByRoleId(String roleId);
}
