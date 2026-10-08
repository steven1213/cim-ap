package com.cim.mq.outbox;

import java.time.Instant;
import java.util.List;

import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MqProducer;
import com.cim.mq.core.SendResult;
import com.cim.mq.core.exception.MqSendException;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.resilience.CircuitBreaker;
import com.cim.mq.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 发件箱中继：轮询 outbox 并投递到 MQ（at-least-once）。
 *
 * <p>投递链：claimBatch → 反序列化信封 → 重试投递（指数退避 + 熔断器）→ 成功则 markSent，
 * 失败则退避后重试。配合消费侧幂等，做到「不丢、不重」（README §12/§13）。</p>
 */
public class OutboxRelay implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStore store;
    private final MqProducer producer;
    private final EventSerializer serializer;
    private final RetryPolicy retry;
    private final CircuitBreaker breaker;
    private final int batchSize;

    public OutboxRelay(OutboxStore store, MqProducer producer, EventSerializer serializer,
                       RetryPolicy retry, CircuitBreaker breaker, int batchSize) {
        this.store = store;
        this.producer = producer;
        this.serializer = serializer;
        this.retry = retry;
        this.breaker = breaker;
        this.batchSize = batchSize;
    }

    @Override
    public void run() {
        if (!breaker.allow()) {
            log.debug("发件箱中继熔断开启，跳过本轮");
            return;
        }
        try {
            List<OutboxRecord> batch = store.claimBatch(batchSize);
            if (batch.isEmpty()) {
                return;
            }
            for (OutboxRecord rec : batch) {
                relayOne(rec);
            }
            breaker.onSuccess();
        } catch (Exception e) {
            breaker.onFailure();
            log.warn("发件箱中继批处理异常", e);
        }
    }

    private void relayOne(OutboxRecord rec) {
        try {
            IntegrationEvent event = serializer.deserialize(rec.payload());
            SendResult result = retry.execute(() -> producer.send(event, event.aggregateId()));
            store.markSent(rec.id());
            log.debug("发件箱投递成功 id={} -> {}", rec.id(), result.topic());
        } catch (MqSendException | IllegalStateException e) {
            int attempt = rec.attemptCount() + 1;
            long backoffMs = retry.backoffMillis(attempt);
            store.markFailed(rec.id(), attempt, truncate(e.getMessage()), Instant.now().plusMillis(backoffMs));
            log.warn("发件箱投递失败 id={} attempt={}，{}ms 后重试", rec.id(), attempt, backoffMs);
        } catch (Exception e) {
            int attempt = rec.attemptCount() + 1;
            store.markFailed(rec.id(), attempt, truncate(e.getMessage()), Instant.now().plusMillis(retry.backoffMillis(attempt)));
            log.error("发件箱投递未知异常 id={}", rec.id(), e);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
