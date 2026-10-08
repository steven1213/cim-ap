package com.cim.system.menu;

import com.cim.jpa.support.BaseRepository;

import java.util.Collection;
import java.util.List;

/** 菜单仓储。 */
public interface SysMenuRepository extends BaseRepository<SysMenu, String> {

    /** 全部菜单（按层级/排序），供构建菜单树。 */
    List<SysMenu> findByOrderBySortNoAsc();

    /** 按 ID 批量加载并排序（按角色可见性过滤后的结果再入树）。 */
    List<SysMenu> findByIdInOrderBySortNoAsc(Collection<String> ids);
}
