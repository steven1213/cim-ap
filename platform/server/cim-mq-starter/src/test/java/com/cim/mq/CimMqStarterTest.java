package com.cim.mq;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.cim.mq.core.Acknowledgment;
import com.cim.mq.core.ConsumerOptions;
import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MessageHandler;
import com.cim.mq.core.MqAdmin;
import com.cim.mq.core.MqTemplate;
import com.cim.mq.core.MqConsumer;
import com.cim.mq.core.MqProducer;
import com.cim.mq.core.ResilientMessageHandler;
import com.cim.mq.core.SendResult;
import com.cim.mq.core.exception.MqConsumeException;
import com.cim.mq.idempotent.IdempotencyStore;
import com.cim.mq.idempotent.InMemoryIdempotencyStore;
import com.cim.mq.outbox.OutboxRecord;
import com.cim.mq.outbox.OutboxRelay;
import com.cim.mq.outbox.OutboxStore;
import com.cim.mq.resilience.CircuitBreaker;
import com.cim.mq.resilience.DeadLetterPolicy;
import com.cim.mq.resilience.RetryPolicy;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.serialize.JacksonEventSerializer;
import com.cim.mq.spi.MqProvider;
import com.cim.mq.spi.MqProviderContext;
import com.cim.mq.spi.MqProviderFactory;
import com.cim.mq.spi.ProviderFeatures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端冒烟测试（无 Spring / 无 broker）：验证供应商中立门面、SPI 发现、
 * 消费幂等（不重）、重试 + 死信（不丢）、发件箱中继发件箱（不丢）。
 */
class CimMqStarterTest {

    // ---- in-memory provider（测试用，不参与真实 broker） ----

    static final class InMemProducer implements MqProducer {
        final List<IntegrationEvent> sent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public SendResult send(IntegrationEvent event, String routingKey) {
            sent.add(event);
            return SendResult.of(event.topic(), event.id());
        }

        @Override
        public void close() {
        }
    }

    static final class InMemConsumer implements MqConsumer {
        MessageHandler handler;

        @Override
        public void start(MessageHandler h) {
            this.handler = h;
        }

        @Override
        public void stop() {
        }
    }

    static final class InMemAdmin implements MqAdmin {
        final Set<String> declared = ConcurrentHashMap.newKeySet();

        @Override
        public void declareTopic(String topic) {
            declared.add(topic);
        }

        @Override
        public void declareDlq(String topic) {
            declared.add(topic + ".DLQ");
        }

        @Override
        public boolean topicExists(String topic) {
            return declared.contains(topic);
        }

        @Override
        public void close() {
        }
    }

    static final class InMemProvider implements MqProvider {
        final InMemProducer producer = new InMemProducer();
        final InMemConsumer consumer = new InMemConsumer();
        final InMemAdmin admin = new InMemAdmin();

        @Override
        public String type() {
            return "inmem";
        }

        @Override
        public ProviderFeatures features() {
            return ProviderFeatures.from(EnumSet.allOf(ProviderFeatures.Feature.class));
        }

        @Override
        public MqProducer createProducer(MqProviderContext ctx) {
            return producer;
        }

        @Override
        public MqConsumer createConsumer(MqProviderContext ctx, ConsumerOptions options) {
            return consumer;
        }

        @Override
        public MqAdmin createAdmin(MqProviderContext ctx) {
            return admin;
        }
    }

    // ---- in-memory outbox store（验证中继投递） ----

    static final class InMemOutboxStore implements OutboxStore {
        final Map<String, OutboxRecord> map = new ConcurrentHashMap<>();
        final List<String> markedSent = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void save(OutboxRecord record) {
            map.put(record.id(), record);
        }

        @Override
        public List<OutboxRecord> claimBatch(int limit) {
            return map.values().stream()
                    .filter(r -> r.status() == OutboxRecord.Status.PENDING
                            && !r.nextRetryAt().isAfter(Instant.now()))
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        @Override
        public void markSent(String id) {
            map.remove(id);
            markedSent.add(id);
        }

        @Override
        public void markFailed(String id, int attemptCount, String error, Instant nextRetryAt) {
            OutboxRecord r = map.get(id);
            if (r != null) {
                r.status(OutboxRecord.Status.PENDING);
                r.attemptCount(attemptCount);
                r.lastError(error);
                r.nextRetryAt(nextRetryAt);
            }
        }

        @Override
        public void delete(String id) {
            map.remove(id);
        }
    }

    // ---- helpers ----

    private static EventSerializer serializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        return new JacksonEventSerializer(mapper);
    }

    private static MqProviderContext ctx() {
        return new MqProviderContext(new com.cim.mq.autoconfigure.CimMqProperties(), serializer());
    }

    private static IntegrationEvent sampleEvent(String id) {
        return IntegrationEvent.builder()
                .id(id).topic("orders").type("OrderCreated")
                .aggregateType("Order").aggregateId("o-1").version(1)
                .payloadType("com.x.Order").payload("{\"id\":\"o-1\"}").build();
    }

    private static MqProducer producerOf(MqProvider p) {
        return p.createProducer(ctx());
    }

    // ---- tests ----

    @Test
    void spiFactoryDiscoversKafkaProvider() {
        MqProviderFactory factory = new MqProviderFactory();
        assertTrue(factory.types().contains("kafka"), "应自动发现 kafka provider");
        assertEquals("kafka", factory.getProvider("kafka").type());
    }

    @Test
    void templateSendRoutesToProviderProducer() {
        InMemProvider p = new InMemProvider();
        MqTemplate tpl = new MqTemplate(p, ctx(), serializer(),
                new InMemoryIdempotencyStore("t:"),
                RetryPolicy.builder().maxAttempts(1).initialBackoff(Duration.ZERO).build(),
                new DeadLetterPolicy(1), Duration.ofHours(1));
        IntegrationEvent e = sampleEvent("e1");
        tpl.send(e);
        assertEquals(1, p.producer.sent.size());
        assertSame(e, p.producer.sent.get(0));
    }

    @Test
    void resilientHandlerDeduplicatesById() throws Exception {
        InMemProvider p = new InMemProvider();
        AtomicInteger counter = new AtomicInteger();
        ResilientMessageHandler rh = new ResilientMessageHandler(
                (e, ack) -> counter.incrementAndGet(),
                new InMemoryIdempotencyStore("t:"), Duration.ofHours(1),
                RetryPolicy.builder().maxAttempts(1).initialBackoff(Duration.ZERO).build(),
                new DeadLetterPolicy(1), producerOf(p));
        IntegrationEvent e = sampleEvent("e2");
        rh.handle(e, Acknowledgment.NOOP);
        rh.handle(e, Acknowledgment.NOOP); // 重复投递
        assertEquals(1, counter.get(), "幂等：同一 eventId 仅处理一次");
    }

    @Test
    void resilientHandlerRetriesThenSucceeds() throws Exception {
        InMemProvider p = new InMemProvider();
        AtomicInteger counter = new AtomicInteger();
        ResilientMessageHandler rh = new ResilientMessageHandler(
                (e, ack) -> {
                    if (counter.incrementAndGet() < 3) {
                        throw new MqConsumeException("fail-" + counter.get());
                    }
                },
                new InMemoryIdempotencyStore("t:"), Duration.ofHours(1),
                RetryPolicy.builder().maxAttempts(3).initialBackoff(Duration.ZERO).factor(1.0).build(),
                new DeadLetterPolicy(3), producerOf(p));
        rh.handle(sampleEvent("e3"), Acknowledgment.NOOP);
        assertEquals(3, counter.get(), "重试 3 次，第 3 次成功");
    }

    @Test
    void resilientHandlerForwardsToDlqOnPermanentFailure() throws Exception {
        InMemProvider p = new InMemProvider();
        ResilientMessageHandler rh = new ResilientMessageHandler(
                (e, ack) -> {
                    throw new MqConsumeException("always");
                },
                new InMemoryIdempotencyStore("t:"), Duration.ofHours(1),
                RetryPolicy.builder().maxAttempts(2).initialBackoff(Duration.ZERO).build(),
                new DeadLetterPolicy(2), producerOf(p));
        rh.handle(sampleEvent("e4"), Acknowledgment.NOOP);
        boolean dlq = p.producer.sent.stream().anyMatch(x -> x.topic().endsWith(".DLQ"));
        assertTrue(dlq, "最终失败应转发至死信队列");
    }

    @Test
    void outboxRelayDeliversAndMarksSent() {
        InMemProvider p = new InMemProvider();
        InMemOutboxStore store = new InMemOutboxStore();
        OutboxRelay relay = new OutboxRelay(store, producerOf(p), serializer(),
                RetryPolicy.builder().maxAttempts(1).initialBackoff(Duration.ZERO).build(),
                new CircuitBreaker(5, Duration.ofMillis(100)), 10);
        IntegrationEvent e = sampleEvent("re1");
        store.save(new OutboxRecord(e.id(), e.aggregateType(), e.aggregateId(),
                e.type(), e.topic(), serializer().serialize(e)));
        relay.run();
        assertEquals(1, p.producer.sent.size());
        assertEquals(e.id(), p.producer.sent.get(0).id());
        assertTrue(store.map.isEmpty(), "投递成功后记录应被标记清除");
    }
}
