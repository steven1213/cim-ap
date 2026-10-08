package com.cim.iam.server.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 令牌黑名单条目（design.md §8.1(h) 存储端）。
 *
 * <p>用户登出 / 主动吊销单条访问令牌时，IAM 把该令牌的 {@code jti} 写入此表；验证侧
 * （platform 的 {@code IamTokenBlacklistChecker}）按 jti 查询，命中即 401。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "token_blacklist", uniqueConstraints = {
        @UniqueConstraint(name = "uk_token_blacklist_jti", columnNames = "jti")
})
public class BlacklistedToken {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "jti", length = 128, nullable = false)
    private String jti;

    @Column(name = "user_id", length = 128)
    private String userId;

    /** 原令牌自然过期时间，供清理作业使用。 */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;
}
