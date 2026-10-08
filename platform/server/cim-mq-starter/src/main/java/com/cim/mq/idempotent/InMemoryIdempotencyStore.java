package com.cim.mq.idempotent;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存幂等去重（无 Redis 时的降级实现，单实例有效）。
 *
 * <p>跨实例不共享；生产环境请改用 {@link RedisIdempotencyStore}。键随 TTL 惰性过期。</p>
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<String, Instant> store = new ConcurrentHashMap<>();
    private final String prefix;

    public InMemoryIdempotencyStore(String prefix) {
        this.prefix = prefix == null ? "mq:idem:" : prefix;
    }

    private String k(String key) {
        return prefix + key;
    }

    @Override
    public boolean isProcessed(String key) {
        return expiry(k(key)) != null;
    }

    @Override
    public void markProcessed(String key, Duration ttl) {
        store.put(k(key), Instant.now().plus(ttl));
    }

    @Override
    public boolean tryMark(String key, Duration ttl) {
        String k = k(key);
        Instant now = Instant.now();
        return store.compute(k, (kk, old) -> {
            if (old == null || old.isBefore(now)) {
                return now.plus(ttl);
            }
            return old;
        }).isAfter(now);
    }

    @Override
    public void clear(String key) {
        store.remove(k(key));
    }

    private Instant expiry(String k) {
        Instant e = store.get(k);
        if (e == null) {
            return null;
        }
        if (e.isBefore(Instant.now())) {
            store.remove(k);
            return null;
        }
        return e;
    }
}
