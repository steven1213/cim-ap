package com.cim.cache.support;

import com.cim.cache.guard.SimpleBloomFilter;

/**
 * BloomFilter 提供者（SPI，由业务 app 实现并注册为 Spring Bean）。
 *
 * <p>缓存 starter 不会自行维护「有效 key 集合」——这必须由业务侧根据自有数据灌入。
 * 例如启动时从 DB 全量加载 ID 构建 {@link SimpleBloomFilter}，或在写入/创建实体时 {@code put}。
 * 未提供此 Bean 时，BloomFilter 防穿透增强自动关闭。</p>
 */
@FunctionalInterface
public interface BloomFilterProvider {

    /**
     * @return 业务侧维护的 BloomFilter（应已灌入「可能存在有效数据的 key 集合」）
     */
    SimpleBloomFilter getBloomFilter();
}
