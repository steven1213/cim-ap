package com.cim.system.support;

/**
 * 权限码工具：规范 {@code module:res:action}（README §21.1）。
 *
 * <p>集中一处拼装/校验，避免散落的字符串字面量导致「注解里写的码」与
 * 「权限表里存的码」漂移——这是 RBAC 最常见的线上故障来源。</p>
 */
public final class PermissionCodes {

    /** 模块名：平台系统域。 */
    public static final String MODULE_SYS = "sys";

    /** 用户管理模块内权限。 */
    public static final String USER_LIST = "sys:user:list";
    public static final String USER_SAVE = "sys:user:save";
    public static final String USER_REMOVE = "sys:user:remove";
    public static final String USER_GRANT = "sys:user:grant";

    /** 角色管理。 */
    public static final String ROLE_LIST = "sys:role:list";
    public static final String ROLE_SAVE = "sys:role:save";
    public static final String ROLE_REMOVE = "sys:role:remove";
    public static final String ROLE_GRANT = "sys:role:grant";

    /** 菜单管理（含角色菜单可见性配置）。 */
    public static final String MENU_LIST = "sys:menu:list";
    public static final String MENU_SAVE = "sys:menu:save";
    public static final String MENU_REMOVE = "sys:menu:remove";

    /** 权限管理。 */
    public static final String PERMISSION_LIST = "sys:permission:list";
    public static final String PERMISSION_SAVE = "sys:permission:save";
    public static final String PERMISSION_REMOVE = "sys:permission:remove";

    /** 字典管理。 */
    public static final String DICT_LIST = "sys:dict:list";
    public static final String DICT_SAVE = "sys:dict:save";
    public static final String DICT_REMOVE = "sys:dict:remove";

    /** 参数配置。 */
    public static final String CONFIG_LIST = "sys:config:list";
    public static final String CONFIG_SAVE = "sys:config:save";
    public static final String CONFIG_REMOVE = "sys:config:remove";

    /** 日志查询。 */
    public static final String LOG_LIST = "sys:log:list";

    private PermissionCodes() {
    }

    /** 拼装权限码。 */
    public static String of(String module, String res, String action) {
        return module + ":" + res + ":" + action;
    }
}
