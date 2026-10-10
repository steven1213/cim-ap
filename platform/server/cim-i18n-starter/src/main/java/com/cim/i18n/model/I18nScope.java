package com.cim.i18n.model;

/**
 * 译文级别（README §7）。
 *
 * <ul>
 *   <li>{@link #SYSTEM}：平台内置（校验/异常/错误码/通用），应用启动种子写入，管理端锁定不可删；</li>
 *   <li>{@link #USER}：业务/UI 文案（菜单名、按钮、字段标签、状态描述），管理后台维护。</li>
 * </ul>
 */
public enum I18nScope {
    SYSTEM,
    USER
}
