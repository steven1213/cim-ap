package com.cim.mq;

import java.time.Instant;

import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.outbox.OutboxRecord;
import com.cim.mq.outbox.OutboxStore;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 集成事件发布器（发件箱路径）。
 *
 * <p>业务在 {@code @Transactional} 内调用 {@link #publish(IntegrationEvent)}，事件信封被写入
 * {@code sys_outbox}（与业务表同事务）；随后由 {@code OutboxRelay} 异步投递到 MQ。
 * 这是「业务成功 ⇔ 消息存在」、从而「不丢消息」的关键（README §12）。</p>
 */
public class OutboxIntegrationEventPublisher {

    private final OutboxStore store;
    private final EventSerializer serializer;

    public OutboxIntegrationEventPublisher(OutboxStore store, EventSerializer serializer) {
        this.store = store;
        this.serializer = serializer;
    }

    public void publish(IntegrationEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxIntegrationEventPublisher.publish 必须在 @Transactional 内调用");
        }
        OutboxRecord rec = new OutboxRecord(
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.type(),
                event.topic(),
                serializer.serialize(event));
        rec.createTime(Instant.now());
        rec.nextRetryAt(Instant.now());
        store.save(rec);
    }
}
