package com.cim.mq.idempotent;

import java.time.Duration;

import com.cim.mq.core.Acknowledgment;
import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MessageHandler;

/**
 * 消费幂等过滤器：包裹业务 {@link MessageHandler}。
 *
 * <p>流程：处理前查 {@link IdempotencyStore}，已处理则直接 ack 跳过；否则执行业务，
 * 成功后标记已处理。配合 at-least-once 投递，实现「不重」（README §13/§16）。</p>
 *
 * <p>去重键默认取 {@code eventId}；状态类事件可改用 {@code aggregateId+version}
 * （由 {@link #byAggregateVersion()} 切换）。</p>
 */
public class EventIdempotencyFilter implements MessageHandler {

    private final MessageHandler delegate;
    private final IdempotencyStore store;
    private final Duration ttl;
    private boolean byAggregateVersion = false;

    public EventIdempotencyFilter(MessageHandler delegate, IdempotencyStore store, Duration ttl) {
        this.delegate = delegate;
        this.store = store;
        this.ttl = ttl;
    }

    public EventIdempotencyFilter byAggregateVersion() {
        this.byAggregateVersion = true;
        return this;
    }

    private String keyOf(IntegrationEvent event) {
        return byAggregateVersion
                ? event.aggregateType() + ":" + event.aggregateId() + ":" + event.version()
                : event.id();
    }

    @Override
    public void handle(IntegrationEvent event, Acknowledgment ack) throws com.cim.mq.core.exception.MqConsumeException {
        String key = keyOf(event);
        if (store.isProcessed(key)) {
            ack.acknowledge();
            return;
        }
        delegate.handle(event, ack);
        store.markProcessed(key, ttl);
    }
}
