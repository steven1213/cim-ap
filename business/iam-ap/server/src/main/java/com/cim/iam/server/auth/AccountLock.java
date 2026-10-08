package com.cim.iam.server.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 登录失败锁定状态（暴力破解防护，design.md §8.1(i) 存储端）。
 *
 * <p>每个被尝试的用户名一行；累计窗口内失败次数，达到阈值即写入 {@code lockedUntil} 触发锁定。
 * 用户名统一小写存储，避免大小写敏感的计数分叉。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "account_lock", uniqueConstraints = {
        @UniqueConstraint(name = "uk_account_lock_username", columnNames = "username")
})
public class AccountLock {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "username", length = 128, nullable = false)
    private String username;

    @Column(name = "fail_count", nullable = false)
    private int failCount = 0;

    /** 窗口内首次失败时间，用于滑动窗口重置判定。 */
    @Column(name = "first_fail_at")
    private Instant firstFailAt;

    @Column(name = "last_fail_at")
    private Instant lastFailAt;

    /** 锁定到期时间；为 null 表示未锁定。 */
    @Column(name = "locked_until")
    private Instant lockedUntil;
}
