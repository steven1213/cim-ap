package com.cim.cache;

import com.cim.cache.support.CacheableCim;
import com.cim.cache.support.CacheEvictCim;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/** 测试服务：演示 @CacheableCim 读缓存 + @CacheEvictCim 写后失效。 */
@Service
public class EquipmentService {

    private final AtomicInteger loadCount = new AtomicInteger();
    private final AtomicInteger saveCount = new AtomicInteger();

    @CacheableCim(value = "equipment", key = "#p0")
    public Equipment load(String id) {
        loadCount.incrementAndGet();
        if ("missing".equals(id)) {
            return null;
        }
        return new Equipment(id, "name-" + id);
    }

    @CacheEvictCim(value = "equipment", key = "#p0.id")
    public Equipment save(Equipment entity) {
        saveCount.incrementAndGet();
        return entity;
    }

    public int getLoadCount() {
        return loadCount.get();
    }

    public int getSaveCount() {
        return saveCount.get();
    }
}
