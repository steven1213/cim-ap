package com.cim.mq.core;

import java.time.Duration;

import com.cim.mq.core.exception.MqConsumeException;
import com.cim.mq.core.exception.MqSendException;
import com.cim.mq.idempotent.IdempotencyStore;
import com.cim.mq.observability.MqTraceInterceptor;
import com.cim.mq.resilience.DeadLetterPolicy;
import com.cim.mq.resilience.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 弹性消费处理器：幂等 + 重试 + 死信 的组合（框架内部统一包裹用户 handler）。
 *
 * <p>顺序：还原链路 → 幂等查重（已处理直接 ack）→ 重试执行业务（指数退避）→
 * 成功则标记幂等；失败达上限转发死信队列并 ack 原始消息终止重投。
 * 与 broker 的 at-least-once 投递共同实现「不丢、不重」（README §13/§16）。</p>
 */
public class ResilientMessageHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(ResilientMessageHandler.class);

    private final MessageHandler delegate;
    private final IdempotencyStore idempotency;
    private final Duration idemTtl;
    private final RetryPolicy retry;
    private final DeadLetterPolicy deadLetter;
    private final MqProducer producer;

    public ResilientMessageHandler(MessageHandler delegate, IdempotencyStore idempotency,
                                   Duration idemTtl, RetryPolicy retry, DeadLetterPolicy deadLetter,
                                   MqProducer producer) {
        this.delegate = delegate;
        this.idempotency = idempotency;
        this.idemTtl = idemTtl;
        this.retry = retry;
        this.deadLetter = deadLetter;
        this.producer = producer;
    }

    @Override
    public void handle(IntegrationEvent event, Acknowledgment ack) throws MqConsumeException {
        MqTraceInterceptor.restoreFrom(event);

        if (idempotency.isProcessed(event.id())) {
            ack.acknowledge();
            return;
        }

        try {
            retry.execute(() -> {
                try {
                    delegate.handle(event, ack);
                } catch (MqConsumeException e) {
                    throw new RuntimeException(e);
                }
            });
            idempotency.markProcessed(event.id(), idemTtl);
            ack.acknowledge(); // 业务成功：提交位点，停止重投
        } catch (Exception e) {
            try {
                forwardToDlq(event);
                idempotency.markProcessed(event.id(), idemTtl); // 已转死信：避免重投时重复转发
                ack.acknowledge();                              // 原始消息确认，终止重投
            } catch (Exception dlqError) {
                ack.negativeAcknowledge();                     // 死信转发失败：保留重投，下轮再试
                log.error("消费最终失败且死信转发失败，等待重投 id={}", event.id(), dlqError);
            }
        }
    }

    private void forwardToDlq(IntegrationEvent event) {
        try {
            IntegrationEvent dlqEvent = IntegrationEvent.builder()
                    .id(event.id() + "-dlq")
                    .topic(deadLetter.dlqTopic(event.topic()))
                    .type(event.type())
                    .aggregateType(event.aggregateType())
                    .aggregateId(event.aggregateId())
                    .version(event.version())
                    .payloadType(event.payloadType())
                    .payload(event.payload())
                    .headers(event.headers())
                    .occurredAt(event.occurredAt())
                    .build();
            producer.send(dlqEvent, event.aggregateId());
        } catch (MqSendException ex) {
            log.error("死信投递失败 id={}", event.id(), ex);
        }
    }
}
