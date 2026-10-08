package com.cim.cache.guard;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

/**
 * 基于 Redis 的分布式互斥锁（跨实例防击穿）。使用 {@code SET key value NX PX} 语义，
 * 到期自动释放（租约），避免持锁实例崩溃导致死锁。
 */
public class RedisLockProvider implements LockProvider {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String prefix;

    public RedisLockProvider(RedisTemplate<String, Object> redisTemplate, String prefix) {
        this.redisTemplate = redisTemplate;
        this.prefix = (prefix == null) ? "" : prefix;
    }

    @Override
    public boolean tryLock(String key, long leaseSeconds) {
        try {
            Boolean ok = redisTemplate.opsForValue()
                    .setIfAbsent(prefix + key, Boolean.TRUE, Duration.ofSeconds(Math.max(1, leaseSeconds)));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // 锁服务异常时降级：放行重建（牺牲一致性换可用性），由业务侧兜底
            return true;
        }
    }

    @Override
    public void unlock(String key) {
        try {
            redisTemplate.delete(prefix + key);
        } catch (Exception ignored) {
            // 锁最终会随租约过期自动释放
        }
    }
}
