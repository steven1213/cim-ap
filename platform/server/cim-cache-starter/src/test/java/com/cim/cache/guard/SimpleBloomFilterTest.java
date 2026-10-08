package com.cim.cache.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SimpleBloomFilter} 单元测试：验证「无假阴性」这一核心语义。
 */
class SimpleBloomFilterTest {

    @Test
    void noFalseNegativeForInsertedKeys() {
        SimpleBloomFilter filter = new SimpleBloomFilter(1 << 16, 4);
        for (int i = 0; i < 1000; i++) {
            filter.put("equipment:" + i);
        }
        for (int i = 0; i < 1000; i++) {
            assertTrue(filter.mightContain("equipment:" + i),
                    "已灌入的 key 必须判为可能存在（无假阴性）");
        }
    }

    @Test
    void uninsertedKeyOnEmptyFilterIsDefinitelyAbsent() {
        SimpleBloomFilter filter = new SimpleBloomFilter(1 << 16, 4);
        assertFalse(filter.mightContain("equipment:unknown"),
                "空过滤器对任何 key 都应判为不存在");
    }

    @Test
    void falsePositiveRateIsAcceptable() {
        int n = 1000;
        SimpleBloomFilter filter = new SimpleBloomFilter(1 << 16, 4);
        for (int i = 0; i < n; i++) {
            filter.put("equipment:" + i);
        }
        int falsePositives = 0;
        for (int i = n; i < n + 10000; i++) {
            if (filter.mightContain("equipment:" + i)) {
                falsePositives++;
            }
        }
        // 1<<16 位、4 哈希、1000 元素时假阳性率应很低（<5%），仅作合理性断言
        assertTrue(falsePositives < 500, "假阳性数应远小于样本量，实际=" + falsePositives);
    }
}
