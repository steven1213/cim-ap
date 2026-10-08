package com.cim.cache.multi;

import com.cim.cache.guard.LocalLockProvider;
import com.cim.cache.guard.MutexRebuild;
import com.cim.cache.guard.NullValueGuard;
import com.cim.cache.guard.TtlJitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MultiLevelCacheLocalTest {

    private MultiLevelCache build() {
        LocalCaffeineCache l1 = new LocalCaffeineCache(100, 60);
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        return new MultiLevelCache(l1, null,
                new NullValueGuard(true, 60),
                new TtlJitter(0.1, 60),
                new MutexRebuild(new LocalLockProvider(), 30),
                60, 300, om, null);
    }

    @Test
    void cachesResultAndLoadsOnce() {
        MultiLevelCache cache = build();
        AtomicInteger calls = new AtomicInteger();
        Object r1 = cache.get("equipment:1", String.class, () -> {
            calls.incrementAndGet();
            return "v1";
        });
        Object r2 = cache.get("equipment:1", String.class, () -> {
            calls.incrementAndGet();
            return "v1";
        });
        assertEquals("v1", r1);
        assertEquals("v1", r2);
        assertEquals(1, calls.get());
    }

    @Test
    void nullValueIsGuardedAgainstPenetration() {
        MultiLevelCache cache = build();
        AtomicInteger calls = new AtomicInteger();
        Object n1 = cache.get("equipment:missing", String.class, () -> {
            calls.incrementAndGet();
            return null;
        });
        Object n2 = cache.get("equipment:missing", String.class, () -> {
            calls.incrementAndGet();
            return null;
        });
        assertNull(n1);
        assertNull(n2);
        assertEquals(1, calls.get()); // 第二次命中空值占位，不再回源
    }

    @Test
    void evictForcesReload() {
        MultiLevelCache cache = build();
        AtomicInteger calls = new AtomicInteger();
        cache.get("equipment:1", String.class, () -> {
            calls.incrementAndGet();
            return "v1";
        });
        cache.evict("equipment:1");
        cache.get("equipment:1", String.class, () -> {
            calls.incrementAndGet();
            return "v1";
        });
        assertEquals(2, calls.get());
    }
}
