package com.cim.cache.multi;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * L2 全局缓存（Redis）。跨实例共享，作为多级缓存的全局层；本地 Caffeine 未命中时回源至此。
 *
 * <p>值使用 {@code GenericJackson2JsonRedisSerializer} 序列化（在构建 {@link RedisTemplate} 时注入），
 * 写入类型信息（{@code @class}），还原时恢复真实类型。</p>
 */
public class RedisCache implements CacheStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String prefix;

    public RedisCache(RedisTemplate<String, Object> redisTemplate, String prefix) {
        this.redisTemplate = redisTemplate;
        this.prefix = (prefix == null) ? "" : prefix;
    }

    @Override
    public Optional<CachedValue> get(String key) {
        try {
            Object o = redisTemplate.opsForValue().get(prefix + key);
            if (o == null) {
                return Optional.empty();
            }
            if (o instanceof CachedValue cv) {
                return Optional.of(cv);
            }
            return Optional.empty();
        } catch (Exception e) {
            // L2 异常不应阻断读路径，交由上层降级
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, CachedValue value, long ttlSeconds) {
        redisTemplate.opsForValue().set(prefix + key, value, Duration.ofSeconds(Math.max(1, ttlSeconds)));
    }

    @Override
    public void evict(String key) {
        redisTemplate.delete(prefix + key);
    }

    @Override
    public void clear() {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public void clearNamespace(String namespace) {
        Set<String> keys = redisTemplate.keys(prefix + namespace + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Override
    public boolean contains(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(prefix + key));
    }

    @Override
    public String name() {
        return "L2-redis";
    }
}
