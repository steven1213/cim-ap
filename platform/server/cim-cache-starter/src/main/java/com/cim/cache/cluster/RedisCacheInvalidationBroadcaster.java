package com.cim.cache.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis pub/sub 的失效事件广播（默认实现）。L2 Redis 已存在时开箱即用，无需引入消息中间件。
 */
public class RedisCacheInvalidationBroadcaster implements CacheInvalidationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationBroadcaster.class);

    private final StringRedisTemplate template;
    private final ObjectMapper objectMapper;

    public RedisCacheInvalidationBroadcaster(StringRedisTemplate template, ObjectMapper objectMapper) {
        this.template = template;
        this.objectMapper = objectMapper;
    }

    @Override
    public void broadcast(CacheInvalidationEvent event) {
        try {
            template.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            // 广播失败不应阻断主流程（本地已失效），仅告警兜底
            log.warn("[cim-cache] 缓存失效事件广播失败 (ns={}, key={}): {}", event.getNamespace(), event.getKey(), e.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
