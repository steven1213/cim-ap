package com.cim.cache.guard;

import com.cim.cache.support.BloomFilterProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BloomFilterGuard} 单元测试：无 provider 时自动关闭；有 provider 时按容器判定。
 */
class BloomFilterGuardTest {

    @Test
    void disabledWhenNoProvider() {
        BloomFilterGuard guard = new BloomFilterGuard(null, true);
        assertFalse(guard.isEnabled(), "未提供 provider 时应自动关闭");
        assertTrue(guard.mightBeValid("anything"), "关闭时一律放行");
    }

    @Test
    void filtersKeysNotInBloom() {
        SimpleBloomFilter filter = new SimpleBloomFilter(1 << 16, 4);
        filter.put("equipment:1");
        BloomFilterGuard guard = new BloomFilterGuard(() -> filter, false);

        assertTrue(guard.isEnabled());
        assertTrue(guard.mightBeValid("equipment:1"), "已灌入 key 放行");
        assertFalse(guard.mightBeValid("equipment:999"), "未灌入 key 判为穿透");
    }

    @Test
    void recordAddsKeyOnLoad() {
        SimpleBloomFilter filter = new SimpleBloomFilter(1 << 16, 4);
        BloomFilterProvider provider = () -> filter;
        BloomFilterGuard guard = new BloomFilterGuard(provider, true);
        assertFalse(guard.mightBeValid("equipment:42"));
        guard.record("equipment:42");
        assertTrue(guard.mightBeValid("equipment:42"), "record 后应放行");
    }
}
