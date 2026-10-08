package com.cim.mq.provider.pulsar;

import java.util.concurrent.ConcurrentHashMap;

import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MqProducer;
import com.cim.mq.core.SendResult;
import com.cim.mq.core.exception.MqSendException;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.spi.MqProviderContext;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;

/**
 * Pulsar 生产者适配。
 *
 * <p>落实「不丢」：{@code producer.newMessage().key(key).value(bytes).send()} 同步等 broker 确认；
 * 路由键 = aggregateId，配合 {@code Key_Shared} 订阅保证<b>同 key 严格有序</b>（Pulsar 原生能力）。
 * 按 topic 懒创建并缓存 Producer。</p>
 */
public class PulsarMqProducer implements MqProducer {

    private final PulsarClient client;
    private final EventSerializer serializer;
    private final String defaultTopic;
    private final boolean enableBatching;
    private final ConcurrentHashMap<String, Producer<byte[]>> producers = new ConcurrentHashMap<>();

    public PulsarMqProducer(MqProviderContext ctx) {
        this.client = buildClient(ctx);
        this.serializer = ctx.serializer();
        this.defaultTopic = ctx.properties().getDefaultTopic();
        this.enableBatching = ctx.properties().getPulsar().isEnableBatching();
    }

    private static PulsarClient buildClient(MqProviderContext ctx) {
        try {
            return PulsarClient.builder()
                    .serviceUrl(ctx.properties().getPulsar().getServiceUrl())
                    .enableTransaction(false)
                    .build();
        } catch (Exception e) {
            throw new MqSendException("创建 Pulsar 客户端失败", e);
        }
    }

    private Producer<byte[]> producerFor(String topic) {
        return producers.computeIfAbsent(topic, t -> {
            try {
                return client.newProducer(Schema.BYTES)
                        .topic(t)
                        .enableBatching(ctxBatch())
                        .create();
            } catch (Exception e) {
                throw new MqSendException("创建 Pulsar Producer 失败: " + t, e);
            }
        });
    }

    private boolean ctxBatch() {
        return enableBatching;
    }

    @Override
    public SendResult send(IntegrationEvent event, String routingKey) throws MqSendException {
        String topic = event.topic() != null ? event.topic() : defaultTopic;
        try {
            MessageId id = producerFor(topic)
                    .newMessage()
                    .key(routingKey)
                    .value(serializer.serialize(event))
                    .send();
            return SendResult.of(topic, event.id()).partitionOrQueue(id.toString());
        } catch (MqSendException e) {
            throw e;
        } catch (Exception e) {
            throw new MqSendException("Pulsar 发送失败: " + topic, e);
        }
    }

    @Override
    public void close() {
        producers.values().forEach(p -> {
            try {
                p.close();
            } catch (Exception ignored) {
            }
        });
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }
}
