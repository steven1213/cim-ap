package com.cim.auth.token;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * 验证后的令牌声明（platform 侧的归一化视图）。
 *
 * <p>由 {@link JwtVerifier} 从 IAM 签发的 JWT 解析得到；
 * 不含任何签发/吊销逻辑（那些在 {@code iam-ap}）。</p>
 *
 * @param userId     用户 ID（缺省取 sub）
 * @param username   用户名（缺省取 sub）
 * @param apps       可进入的 ap 列表（准入依据）
 * @param roles      ap 内粗角色组（可选）
 * @param tenantId   租户 ID
 * @param authorities 本 ap 业务权限集（module:res:action）
 * @param version    令牌版本（本地失效判定）
 * @param expiresAt  过期时间
 * @param raw        原始声明集合
 */
public record TokenClaims(
        String userId,
        String username,
        Set<String> apps,
        Set<String> roles,
        String tenantId,
        Set<String> authorities,
        Long version,
        Instant expiresAt,
        Map<String, Object> raw) {
}
