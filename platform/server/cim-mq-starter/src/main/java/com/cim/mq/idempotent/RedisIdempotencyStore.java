package com.cim.mq.idempotent;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis 的幂等去重（推荐，跨实例共享）。
 *
 * <p>利用 {@code SET key value NX EX} 原子语义实现跨实例去重；键过期即视为可重新处理，
 * 具备自愈能力。由自动装配在存在 {@code StringRedisTemplate} 时选用。</p>
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redis;
    private final String prefix;

    public RedisIdempotencyStore(StringRedisTemplate redis, String prefix) {
        this.redis = redis;
        this.prefix = prefix == null ? "mq:idem:" : prefix;
    }

    private String k(String key) {
        return prefix + key;
    }

    @Override
    public boolean isProcessed(String key) {
        return Boolean.TRUE.equals(redis.hasKey(k(key)));
    }

    @Override
    public void markProcessed(String key, Duration ttl) {
        redis.opsForValue().set(k(key), "1", ttl);
    }

    @Override
    public boolean tryMark(String key, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(k(key), "1", ttl));
    }

    @Override
    public void clear(String key) {
        redis.delete(k(key));
    }
}
