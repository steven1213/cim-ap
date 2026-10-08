package com.cim.system.menu;

/**
 * 菜单节点类型（design.md §2.10 / README §21.1）。
 *
 * <p>菜单（导航）与权限（后端 API / 按钮）**分离**：{@code BUTTON} 节点只承载
 * {@code perm_code}（按钮级权限码），不参与路由；{@code DIR}/{@code MENU} 才是导航节点。
 * 两者都通过 {@code sys_role_menu} 按角色授予可见性。</p>
 */
public enum MenuType {

    /** 目录（导航分组，无组件）。 */
    DIR,

    /** 菜单（可路由页面）。 */
    MENU,

    /** 按钮（仅权限码，不出现在导航中）。 */
    BUTTON
}
