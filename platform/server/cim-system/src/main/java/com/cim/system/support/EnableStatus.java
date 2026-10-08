package com.cim.system.support;

/**
 * 系统域通用启用状态（用户 / 角色 / 菜单 / 权限 / 字典 / 参数共用）。
 *
 * <p>以枚举 + {@code EnumType.STRING} 落库，避免魔法字符串；停用（{@code DISABLED}）的
 * 用户不参与权限计算（见 {@code DbLocalAuthorityLoader}）。</p>
 */
public enum EnableStatus {

    /** 启用。 */
    ENABLED,

    /** 停用。 */
    DISABLED;

    /** 是否启用。 */
    public boolean enabled() {
        return this == ENABLED;
    }
}
