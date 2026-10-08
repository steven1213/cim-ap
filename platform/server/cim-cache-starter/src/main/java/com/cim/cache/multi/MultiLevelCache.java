package com.cim.cache.multi;

import com.cim.cache.guard.BloomFilterGuard;
import com.cim.cache.guard.MutexRebuild;
import com.cim.cache.guard.NullValueGuard;
import com.cim.cache.guard.TtlJitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 多级缓存核心：组合 L1（本地 Caffeine）+ 可选 L2（Redis 全局）。
 *
 * <p>读路径：L1 → L2 → loader 回填两级；写（失效）同时清两级。
 * 内置三守卫：
 * <ul>
 *   <li>防穿透：空结果缓存短 TTL 占位（{@link NullValueGuard}）</li>
 *   <li>防击穿：回源加互斥锁，单线程重建（{@link MutexRebuild} + {@link com.cim.cache.guard.LockProvider}）</li>
 *   <li>防雪崩：L2 真实值 TTL 加随机抖动（{@link TtlJitter}）</li>
 * </ul>
 *
 * <p>L2 不可用时自动降级为仅 L1（见 {@code RedisCache.get} 的异常吞没与 {@link #writeBoth} 的容错）。</p>
 */
@Slf4j
public class MultiLevelCache {

    private final CacheStore l1;
    private final CacheStore l2;
    private final List<CacheStore> stores;
    private final NullValueGuard nullGuard;
    private final TtlJitter jitter;
    private final MutexRebuild mutex;
    private final long l1Ttl;
    private final long l2DefaultTtl;
    private final long nullTtl;
    private final ObjectMapper objectMapper;
    private final BloomFilterGuard bloomGuard;

    public MultiLevelCache(CacheStore l1, CacheStore l2, NullValueGuard nullGuard, TtlJitter jitter,
                          MutexRebuild mutex, long l1Ttl, long l2DefaultTtl, ObjectMapper objectMapper,
                          BloomFilterGuard bloomGuard) {
        this.l1 = l1;
        this.l2 = l2;
        this.stores = new ArrayList<>(2);
        this.stores.add(l1);
        if (l2 != null) {
            this.stores.add(l2);
        }
        this.nullGuard = nullGuard;
        this.jitter = jitter;
        this.mutex = mutex;
        this.l1Ttl = l1Ttl;
        this.l2DefaultTtl = l2DefaultTtl;
        this.nullTtl = nullGuard.isEnabled() ? nullGuard.ttlSeconds() : l2DefaultTtl;
        this.objectMapper = objectMapper;
        this.bloomGuard = bloomGuard;
    }

    /**
     * 读取（未命中则经互斥重建回源）。
     *
     * @param key    完整缓存键（含命名空间/前缀）
     * @param type   期望返回类型
     * @param loader 回源函数（通常为查 DB），仅在缓存未命中且获得重建锁时执行
     * @param <T>    返回类型
     * @return 缓存值或 loader 结果；查无数据返回 {@code null}
     */
    public Object get(String key, Class<?> type, Supplier<?> loader) {
        CachedValue cv = readThrough(key);
        if (cv != null) {
            return unwrap(cv, type);
        }

        // 防穿透增强（可选 BloomFilter）：key 确定不在有效集合中 → 直接回空，绝不查 DB。
        // 注意：此处不写空值占位——BloomFilter 本身即「不存在的廉价索引」，写占位反而会
        // 掩盖后续被 recordValidKey 补录的有效 key（占位未过期前一直命中 null）。
        if (bloomGuard != null && bloomGuard.isEnabled() && !bloomGuard.mightBeValid(key)) {
            return null;
        }

        // 未命中：互斥重建（带重试，等待其它线程先建好）
        int retries = 0;
        while (retries < 5) {
            if (mutex.tryLock(key)) {
                try {
                    cv = readThrough(key); // double-check
                    if (cv != null) {
                        return unwrap(cv, type);
                    }
                    Object value = loader.get();
                    cv = nullGuard.wrap(value);
                    if (bloomGuard != null && bloomGuard.isEnabled() && bloomGuard.isAddOnLoad() && !cv.isNullValue()) {
                        bloomGuard.record(key);
                    }
                    long l2Ttl = cv.isNullValue() ? nullTtl : jitter.apply(l2DefaultTtl);
                    writeBoth(key, cv, l2Ttl);
                    return unwrap(cv, type);
                } finally {
                    mutex.unlock(key);
                }
            } else {
                // 别的线程正在重建，短暂等待后重试读缓存
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                cv = readThrough(key);
                if (cv != null) {
                    return unwrap(cv, type);
                }
                retries++;
            }
        }

        // 兜底：多次未抢到锁，直接回源（牺牲一点一致性换可用性）
        log.warn("[cim-cache] 重建锁竞争激烈，降级直接回源 key={}", key);
        return loader.get();
    }

    /** 主动写入（绕过守卫，供写路径回填使用）。 */
    public void put(String key, Object value, long l2TtlSeconds) {
        writeBoth(key, CachedValue.of(value), l2TtlSeconds);
    }

    /**
     * 把 key 补录为「有效」（写路径调用，仅在启用 BloomFilter 时有实际效果）。
     *
     * <p>BloomFilter 无法删除元素，故「新增/更新实体」后须把其 key 补录进去，
     * 否则后续按该 key 读取会被判定为穿透而误返回空。删除实体时无需处理（残留项仅造成假阳性）。</p>
     */
    public void recordValidKey(String key) {
        if (bloomGuard != null && bloomGuard.isEnabled()) {
            bloomGuard.record(key);
        }
    }

    /** 失效：清空所有层级的该 key（Cache-Aside 写后删缓存）。 */
    public void evict(String key) {
        for (CacheStore store : stores) {
            try {
                store.evict(key);
            } catch (Exception e) {
                log.warn("[cim-cache] 失效 {} 失败 ({}): {}", store.name(), key, e.getMessage());
            }
        }
    }

    /** 按命名空间失效（{@code @CacheEvictCim(allEntries=true)}）。 */
    public void evictNamespace(String namespace) {
        for (CacheStore store : stores) {
            try {
                store.clearNamespace(namespace);
            } catch (Exception e) {
                log.warn("[cim-cache] 失效命名空间 {} 失败 ({}): {}", store.name(), namespace, e.getMessage());
            }
        }
    }

    private CachedValue readThrough(String key) {
        Optional<CachedValue> o = l1.get(key);
        if (o.isPresent()) {
            return o.get();
        }
        if (l2 != null) {
            Optional<CachedValue> o2 = l2.get(key);
            if (o2.isPresent()) {
                CachedValue cv = o2.get();
                // 回填 L1：空值占位也回填，避免反复查 L2
                l1.put(key, cv, l1Ttl);
                return cv;
            }
        }
        return null;
    }

    private void writeBoth(String key, CachedValue cv, long l2Ttl) {
        l1.put(key, cv, l1Ttl);
        if (l2 != null) {
            try {
                l2.put(key, cv, l2Ttl);
            } catch (Exception e) {
                log.warn("[cim-cache] L2 写入失败，仅保留 L1 ({}): {}", key, e.getMessage());
            }
        }
    }

    private Object unwrap(CachedValue cv, Class<?> type) {
        if (cv == null || cv.isNullValue()) {
            return null;
        }
        Object v = cv.getValue();
        if (v == null) {
            return null;
        }
        if (type.isInstance(v)) {
            return type.cast(v);
        }
        // Redis GenericJackson 通常已还原真实类型；兜底用 Jackson 转换（处理 Map→POJO 等）
        return objectMapper.convertValue(v, type);
    }
}
