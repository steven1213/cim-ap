package com.cim.auth.rbac;

import com.cim.auth.token.TokenClaims;

import java.util.Set;

/**
 * M3 默认权限加载器：直接取令牌 {@code authorities} claim。
 *
 * <p>用于 IAM 已按本 ap 角色注入权限集的场景；M6 由 {@code cim-system} 的
 * 真实 RBAC 实现（查库 + 角色菜单关联）替换，接口不变。</p>
 */
public class ClaimLocalAuthorityLoader implements LocalAuthorityLoader {

    @Override
    public Set<String> loadAuthorities(TokenClaims claims) {
        return claims == null ? Set.of() : claims.authorities();
    }
}
