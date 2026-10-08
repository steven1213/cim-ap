package com.cim.iam.server.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 刷新令牌（design.md §8.1(h) 刷新流程）。
 *
 * <p>库内仅存散列（SHA-256）与元数据，不存明文；明文仅在签发时一次性返回客户端。
 * 每次刷新轮转时旧令牌立即置 {@code revoked}，防重放。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    /** 明文刷新令牌的 SHA-256 散列。 */
    @Column(name = "token_hash", length = 128, nullable = false)
    private String tokenHash;

    /** 关联访问令牌的 jti（签发时记录，便于审计追踪）。 */
    @Column(name = "access_token_jti", length = 128)
    private String accessTokenJti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
