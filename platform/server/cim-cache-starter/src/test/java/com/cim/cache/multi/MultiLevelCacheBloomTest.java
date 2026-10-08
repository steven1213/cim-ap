package com.cim.cache.multi;

import com.cim.cache.guard.BloomFilterGuard;
import com.cim.cache.guard.LocalLockProvider;
import com.cim.cache.guard.MutexRebuild;
import com.cim.cache.guard.NullValueGuard;
import com.cim.cache.guard.SimpleBloomFilter;
import com.cim.cache.guard.TtlJitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * BloomFilter 防穿透增强与 {@link MultiLevelCache} 的协同：确定不存在的 key 直接短路，绝不回源。
 */
class MultiLevelCacheBloomTest {

    private MultiLevelCache build(BloomFilterGuard guard) {
        LocalCaffeineCache l1 = new LocalCaffeineCache(100, 60);
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        return new MultiLevelCache(l1, null,
                new NullValueGuard(true, 60),
                new TtlJitter(0, 0),
                new MutexRebuild(new LocalLockProvider(), 30),
                60, 300, om, guard);
    }

    @Test
    void bloomShortCircuitsUnknownKeyWithoutLoading() {
        SimpleBloomFilter filter = new SimpleBloomFilter(1 << 16, 4);
        filter.put("equipment:1"); // 仅 1 号是有效 key
        MultiLevelCache cache = build(new BloomFilterGuard(() -> filter, false));

        AtomicInteger calls = new AtomicInteger();
        Object r = cache.get("equipment:999", String.class, () -> {
            calls.incrementAndGet();
            return "SHOULD_NOT_LOAD";
        });
        assertNull(r, "确定不存在的 key 应返回空");
        assertEquals(0, calls.get(), "BloomFilter 命中穿透时绝不能回源");
    }

    @Test
    void knownKeyStillLoadsNormally() {
        SimpleBloomFilter filter = new SimpleBloomFilter(1 << 16, 4);
        filter.put("equipment:1");
        MultiLevelCache cache = build(new BloomFilterGuard(() -> filter, false));

        AtomicInteger calls = new AtomicInteger();
        Object r = cache.get("equipment:1", String.class, () -> {
            calls.incrementAndGet();
            return "v1";
        });
        assertEquals("v1", r);
        assertEquals(1, calls.get());
    }

    @Test
    void recordValidKeyUnblocksLaterLookup() {
        SimpleBloomFilter filter = new SimpleBloomFilter(1 << 16, 4);
        MultiLevelCache cache = build(new BloomFilterGuard(() -> filter, false));

        AtomicInteger calls = new AtomicInteger();
        // 首次：BloomFilter 未灌入该 key → 判为穿透，直接短路（且不写空值占位）
        assertNull(cache.get("equipment:7", String.class, () -> {
            calls.incrementAndGet();
            return "v7";
        }));
        assertEquals(0, calls.get(), "未灌入且 BloomFilter 启用时首查即短路（防穿透的代价）");

        // 写路径补录（等价于 @CacheEvictCim 保存实体后自动 recordValidKey）→ 之后即可正常回源
        cache.recordValidKey("equipment:7");
        assertEquals("v7", cache.get("equipment:7", String.class, () -> {
            calls.incrementAndGet();
            return "v7";
        }));
        assertEquals(1, calls.get(), "补录后应正常回源");
    }
}
