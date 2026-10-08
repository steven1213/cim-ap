package com.cim.cache.multi;

import java.util.Optional;

/**
 * 单级缓存存储抽象（L1 本地 / L2 全局）。多级缓存由 {@link MultiLevelCache} 组合若干 {@code CacheStore}。
 */
public interface CacheStore {

    /** 读取；未命中返回 {@code Optional.empty()}。 */
    Optional<CachedValue> get(String key);

    /** 写入（TTL 秒；<=0 由具体实现决定不过期策略）。 */
    void put(String key, CachedValue value, long ttlSeconds);

    /** 失效（删除）。 */
    void evict(String key);

    /** 清空本存储全部键。 */
    void clear();

    /** 按命名空间清空（{@code globalPrefix:namespace:*}）；本地缓存无法精确前缀时清空全部。 */
    void clearNamespace(String namespace);

    /** 是否存在（含空值占位）。 */
    boolean contains(String key);

    /** 存储层级名（L1 / L2），用于日志与诊断。 */
    String name();
}
