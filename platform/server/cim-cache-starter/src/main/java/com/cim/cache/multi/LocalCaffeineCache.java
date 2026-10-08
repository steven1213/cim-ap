package com.cim.cache.multi;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Optional;

/**
 * L1 本地缓存（Caffeine）。纳秒级命中，挡住热点 key、降低 L2（Redis）压力与 RT。
 *
 * <p>说明：Caffeine 的写后 TTL 在构建时统一设定（{@code caffeine.ttl}），所有条目（含空值占位）共用该 TTL。
 * 对空值占位的「精确短 TTL」主要由 L2 保证；L1 仍能在占位 TTL 内有效拦截对 DB 的重复穿透。</p>
 */
public class LocalCaffeineCache implements CacheStore {

    private final Cache<String, CachedValue> cache;

    public LocalCaffeineCache(int maxSize, long ttlSeconds) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(maxSize);
        if (ttlSeconds > 0) {
            builder.expireAfterWrite(Duration.ofSeconds(ttlSeconds));
        }
        this.cache = builder.build();
    }

    @Override
    public Optional<CachedValue> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    @Override
    public void put(String key, CachedValue value, long ttlSeconds) {
        cache.put(key, value);
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    @Override
    public void clearNamespace(String namespace) {
        // 本地缓存无法按命名空间精确筛选，清空全部（L1 为易失层，影响可控）
        cache.invalidateAll();
    }

    @Override
    public boolean contains(String key) {
        return cache.getIfPresent(key) != null;
    }

    @Override
    public String name() {
        return "L1-caffeine";
    }
}
