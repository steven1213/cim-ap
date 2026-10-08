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
 * 用户令牌版本（design.md §8.1(g) 存储/递增端）。
 *
 * <p>每个用户一条记录：业务动作（改权限 / 改密 / 强制下线）后 {@code tokenVersion} bump，
 * 已签发的旧令牌因 {@code ver} 不匹配被验证端判定失效（→ 401）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "token_version", uniqueConstraints = {
        @UniqueConstraint(name = "uk_token_version_user", columnNames = "user_id")
})
public class TokenVersion {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    /** 令牌版本号（非 JPA 乐观锁，专用字段）。bump 自增。 */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Column(name = "bumped_at")
    private Instant bumpedAt;
}
