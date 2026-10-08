package com.cim.iam.server.auth;

import com.cim.core.port.IdGenerator;
import com.cim.iam.server.config.IamAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 登录失败锁定服务（暴力破解防护，design.md §8.1(i) 逻辑端）。
 *
 * <p>核心规则：</p>
 * <ul>
 *   <li>{@link #isLocked}：用户名在 {@code lockedUntil} 之前视为已锁定；过期则懒清理；</li>
 *   <li>{@link #onFailure}：窗口内累计失败，达到 {@code maxAttempts} 即锁定 {@code lockMinutes} 分钟；
 *       首败超出窗口则计数重置（滑动窗口）；</li>
 *   <li>{@link #onSuccess}：登录成功清零计数，使锁定永不因成功登录而长期生效。</li>
 * </ul>
 *
 * <p>用户名统一小写后存储，避免大小写分叉计数。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockService {

    private final AccountLockRepository repository;
    private final IamAuthProperties properties;
    private final IdGenerator idGenerator;

    /** 该用户名当前是否处于锁定状态（过期会自动清理）。 */
    @Transactional(readOnly = true)
    public boolean isLocked(String rawUsername) {
        String username = normalize(rawUsername);
        AccountLock lock = repository.findByUsername(username).orElse(null);
        if (lock == null || lock.getLockedUntil() == null) {
            return false;
        }
        if (lock.getLockedUntil().isBefore(Instant.now())) {
            repository.delete(lock); // 懒清理过期锁定
            return false;
        }
        return true;
    }

    /** 剩余可尝试次数（锁定状态返回 0）。 */
    @Transactional(readOnly = true)
    public int remainingAttempts(String rawUsername) {
        if (isLocked(rawUsername)) {
            return 0;
        }
        AccountLock lock = repository.findByUsername(normalize(rawUsername)).orElse(null);
        int used = (lock == null) ? 0 : lock.getFailCount();
        return Math.max(0, properties.getLockout().getMaxAttempts() - used);
    }

    /** 一次失败：累计计数，达阈值即锁定。 */
    @Transactional
    public void onFailure(String rawUsername) {
        String username = normalize(rawUsername);
        int max = properties.getLockout().getMaxAttempts();
        int window = properties.getLockout().getWindowMinutes();
        int lockMinutes = properties.getLockout().getLockMinutes();
        Instant now = Instant.now();

        AccountLock lock = repository.findByUsername(username).orElseGet(() -> {
            AccountLock l = new AccountLock();
            l.setId(idGenerator.nextId());
            l.setUsername(username);
            return l;
        });

        // 滑动窗口：首败已超出窗口则重置（避免历史失败永久累计）
        if (lock.getFirstFailAt() != null
                && lock.getFirstFailAt().plus(Duration.ofMinutes(window)).isBefore(now)) {
            lock.setFailCount(0);
            lock.setLockedUntil(null);
        }

        lock.setFailCount(lock.getFailCount() + 1);
        if (lock.getFirstFailAt() == null) {
            lock.setFirstFailAt(now);
        }
        lock.setLastFailAt(now);

        if (lock.getFailCount() >= max) {
            lock.setLockedUntil(now.plus(Duration.ofMinutes(lockMinutes)));
            log.warn("[lockout] 账户锁定 user={} 至 {}（失败 {} 次）", username, lock.getLockedUntil(), lock.getFailCount());
        }
        repository.save(lock);
    }

    /** 一次成功：清零该用户名计数。 */
    @Transactional
    public void onSuccess(String rawUsername) {
        String username = normalize(rawUsername);
        repository.findByUsername(username).ifPresent(repository::delete);
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
