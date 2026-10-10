package com.cim.rms.server.security;

import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.token.TokenClaims;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * RMS 业务权限加载器（{@code LocalAuthorityLoader} SPI 实现）。
 *
 * <p>⚠️ <b>临时实现（W2 占位）</b>：直接基于令牌声明推导 rms 权限集，便于前端联调与 E2E 验证。
 * 正式的 RBAC（角色 → 权限的库内配置，复用平台 {@code cim-system} 的 {@code sys_role_perm}）
 * 通过 {@code cim-system} 的真实实现替换本类，接口不变（design.md §2.4）。</p>
 *
 * <p>推导规则：</p>
 * <ul>
 *   <li>令牌 authorities 含 {@code SUPER_ADMIN}（或 {@code ROLE_SUPER}）→ 全量权限；</li>
 *   <li>roles / authorities 含 RMS 管理员标识（{@code RMS_ADMIN} / {@code rms:admin}）→ 全量权限；</li>
 *   <li>否则仅回显令牌中已注入的 {@code rms:*} 权限码，无则取只读集。</li>
 * </ul>
 */
@Component
public class RmsAuthorityLoader implements LocalAuthorityLoader {

    @Override
    public Set<String> loadAuthorities(TokenClaims claims) {
        if (claims == null) return Set.of();

        Set<String> authorities = claims.authorities() == null ? Set.of() : claims.authorities();
        Set<String> roles = claims.roles() == null ? Set.of() : claims.roles();

        boolean superAdmin = authorities.contains("SUPER_ADMIN") || authorities.contains("ROLE_SUPER");
        boolean rmsAdmin = containsAny(roles, "RMS_ADMIN", "rms_admin", "RMS:ADMIN")
                || containsAny(authorities, "rms:admin", "rms:console:admin");

        if (superAdmin || rmsAdmin) {
            return RmsPermissions.full();
        }

        // 回显 IAM 已注入的 rms:* 权限码（未来 cim-system RBAC 注入时即生效）
        Set<String> derived = new HashSet<>();
        for (String a : authorities) {
            if (a.startsWith("rms:")) derived.add(a);
        }
        if (derived.isEmpty()) {
            // 已登录但无明确授权：给只读，避免整页 403 白屏
            return RmsPermissions.readonly();
        }
        return derived;
    }

    private static boolean containsAny(Set<String> set, String... needles) {
        if (set == null) return false;
        for (String n : needles) {
            if (set.contains(n)) return true;
        }
        return false;
    }
}
