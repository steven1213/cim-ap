package com.cim.notify.fanout;

import com.cim.notify.api.NotifyEvent;
import com.cim.notify.transport.SseRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

/**
 * Redis 订阅者（镜像 {@code RedisInvalidationSubscriber}）：收到事件 → 反序列化 → 本 Pod 本地投递。
 */
public class RedisSseFanoutSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisSseFanoutSubscriber.class);

    private final SseRegistry registry;
    private final ObjectMapper objectMapper;

    public RedisSseFanoutSubscriber(SseRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            NotifyEvent event = objectMapper.readValue(message.getBody(), NotifyEvent.class);
            registry.deliverLocal(event);
        } catch (Exception e) {
            log.warn("[cim-notify] subscriber deserialize failed: {}", e.getMessage());
        }
    }
}
