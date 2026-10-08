package com.cim.system.rbac;

import java.util.Collections;
import java.util.Set;

/**
 * 一次 RBAC 解析的结果（三角色语义 + 权限码集）。
 *
 * <p>{@code superRole=true} 时 {@code codes} <b>仍被填充</b>为全量启用权限码：这样
 * {@code @PreAuthorize("hasAuthority('sys:user:list')")} 对超管也成立（{@code hasAuthority}
 * 不认识超管标记），同时 {@link #superRole()} 供需要显式判别的场景（如审计、前端展示）使用。
 * 仅在 {@code CimPermissionEvaluator} 的 {@code hasPermission(...)} 路径上，超管才靠标记短路。</p>
 *
 * @param superRole 是否命中超管角色（{@code sys_role.is_super = true}）
 * @param codes     有效权限码集（超管为全量启用权限码）
 */
public record ResolvedAuthorities(boolean superRole, Set<String> codes) {

    public ResolvedAuthorities {
        codes = codes == null ? Set.of() : Collections.unmodifiableSet(codes);
    }

    /** 无角色（未授权）。 */
    public static ResolvedAuthorities none() {
        return new ResolvedAuthorities(false, Set.of());
    }

    /** 普通授权。 */
    public static ResolvedAuthorities of(Set<String> codes) {
        return new ResolvedAuthorities(false, codes);
    }

    /** 超管授权（附带全量权限码）。 */
    public static ResolvedAuthorities superRole(Set<String> allEnabledCodes) {
        return new ResolvedAuthorities(true, allEnabledCodes);
    }

    /** 是否没有任何有效权限（超管不算空）。 */
    public boolean empty() {
        return !superRole && codes.isEmpty();
    }
}
