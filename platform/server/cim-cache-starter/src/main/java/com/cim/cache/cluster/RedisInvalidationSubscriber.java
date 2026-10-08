package com.cim.cache.cluster;

import com.cim.cache.multi.MultiLevelCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * 跨节点失效事件订阅端：收到事件后使「本节点」本地多级缓存失效（仅清 L1；L2 为全局共享，
 * 由发起节点在 {@code @CacheEvictCim} 时已删除，此处再删一次亦幂等）。每个节点各自持有一个本订阅者，
 * 仅清自己的 L1，从而实现多实例最终一致。
 */
public class RedisInvalidationSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisInvalidationSubscriber.class);

    private final MultiLevelCache localCache;
    private final ObjectMapper objectMapper;

    public RedisInvalidationSubscriber(MultiLevelCache localCache, ObjectMapper objectMapper) {
        this.localCache = localCache;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            CacheInvalidationEvent event = objectMapper.readValue(message.getBody(), CacheInvalidationEvent.class);
            if (event.isAllEntries()) {
                localCache.evictNamespace(event.getNamespace());
            } else {
                localCache.evict(event.getKey());
            }
        } catch (Exception e) {
            log.warn("[cim-cache] 处理缓存失效事件失败: {}", e.getMessage());
        }
    }
}
