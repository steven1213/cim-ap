package com.cim.auth.rbac;

import com.cim.auth.token.TokenClaims;

import java.util.Set;

/**
 * 本 ap 业务权限集加载器（design.md §2.4 / T3.5）。
 *
 * <p>接口由 platform 定义，实现在 {@code cim-system}（对接本 ap 的 RBAC 表），
 * 即「业务内部权限各 ap 自管」的接缝。M3 默认实现 {@link ClaimLocalAuthorityLoader}
 * 直接取令牌 {@code authorities} claim（IAM 签发时已按本 ap 角色注入）；M6 由真实 RBAC 实现替换。</p>
 */
public interface LocalAuthorityLoader {

    /**
     * 按已验证的令牌加载本 ap 的业务权限集（module:res:action）。
     *
     * @param claims 已验证的令牌声明
     * @return 权限码集合（不可为 null）
     */
    Set<String> loadAuthorities(TokenClaims claims);
}
