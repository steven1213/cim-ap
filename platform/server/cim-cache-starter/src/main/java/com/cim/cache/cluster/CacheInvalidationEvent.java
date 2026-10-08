package com.cim.cache.cluster;

import lombok.Data;

/**
 * 跨节点缓存失效事件：由某节点在 {@code @CacheEvictCim} 成功后广播，其它节点订阅后使本地缓存失效，
 * 实现多实例缓存一致（见 README §11 / design.md §2.5）。
 *
 * <p>序列化走 JSON（{@code StringRedisTemplate} + Jackson），跨语言可读；通道见
 * {@link CacheInvalidationBroadcaster#CHANNEL}。</p>
 */
@Data
public class CacheInvalidationEvent {

    /** 命名空间（缓存名），与 {@code @CacheableCim#value()} 对应。 */
    private String namespace;

    /** 失效的单条 key（{@code allEntries=true} 时忽略）。 */
    private String key;

    /** 是否清空整个命名空间（批量/全量变更）。 */
    private boolean allEntries;

    public CacheInvalidationEvent() {
    }

    public CacheInvalidationEvent(String namespace, String key, boolean allEntries) {
        this.namespace = namespace;
        this.key = key;
        this.allEntries = allEntries;
    }
}
