package com.cim.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 集成测试：验证 @CacheableCim 缓存命中、防穿透（空值占位）、@CacheEvictCim 写后失效。
 * Redis 已关闭（cim.cache.redis.enabled=false），走本地 Caffeine 降级路径。
 */
@SpringBootTest(properties = {
        "cim.cache.redis.enabled=false",
        "cim.cache.key-prefix=cimtest"
})
class CacheableCimIntegrationTest {

    @Autowired
    private EquipmentService service;

    @Test
    void cacheHit() {
        int before = service.getLoadCount();
        Equipment e1 = service.load("a");
        Equipment e2 = service.load("a");
        assertEquals(e1, e2);
        assertEquals(before + 1, service.getLoadCount()); // 第二次命中缓存，未回源
    }

    @Test
    void nullValueGuarded() {
        int before = service.getLoadCount();
        service.load("missing");
        service.load("missing");
        assertEquals(before + 1, service.getLoadCount()); // 第二次命中空值占位
        assertNull(service.load("missing")); // 第三次仍命中空值占位（防穿透）
    }

    @Test
    void evictOnSave() {
        int before = service.getLoadCount();
        service.load("b");
        assertEquals(before + 1, service.getLoadCount());
        service.save(new Equipment("b", "x"));
        service.load("b"); // 失效后应重新回源
        assertEquals(before + 2, service.getLoadCount());
    }
}
