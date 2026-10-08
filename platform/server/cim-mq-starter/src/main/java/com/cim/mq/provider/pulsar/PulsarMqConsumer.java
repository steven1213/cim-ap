package com.cim.mq.provider.pulsar;

import java.util.concurrent.atomic.AtomicBoolean;

import com.cim.mq.core.Acknowledgment;
import com.cim.mq.core.ConsumerOptions;
import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MessageHandler;
import com.cim.mq.core.MqConsumer;
import com.cim.mq.core.exception.MqConsumeException;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.spi.MqProviderContext;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;

/**
 * Pulsar 消费者适配（手动 ack）。
 *
 * <p>采用 {@code Key_Shared} 订阅：同一 routingKey 的消息由同一消费者按序投递，天然满足
 * 「按 key 有序」；处理成功才 {@code acknowledge}，失败 {@code negativeAcknowledge} 触发重投。</p>
 */
public class PulsarMqConsumer implements MqConsumer {

    private final PulsarClient client;
    private final EventSerializer serializer;
    private final ConsumerOptions options;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Consumer<byte[]> consumer;
    private Thread loop;

    public PulsarMqConsumer(MqProviderContext ctx, ConsumerOptions options) {
        try {
            this.client = PulsarClient.builder()
                    .serviceUrl(ctx.properties().getPulsar().getServiceUrl())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("创建 Pulsar 客户端失败", e);
        }
        this.serializer = ctx.serializer();
        this.options = options;
    }

    @Override
    public void start(MessageHandler handler) {
        try {
            this.consumer = client.newConsumer(Schema.BYTES)
                    .topic(options.topic())
                    .subscriptionName(options.group())
                    .subscriptionType(SubscriptionType.Key_Shared)
                    .subscribe();
        } catch (Exception e) {
            throw new IllegalStateException("订阅 Pulsar topic 失败: " + options.topic(), e);
        }
        loop = Thread.ofVirtual().name("cim-mq-pulsar-" + options.topic()).start(() -> {
            while (running.get()) {
                try {
                    Message<byte[]> msg = consumer.receive();
                    IntegrationEvent event = serializer.deserialize(msg.getData());
                    Acknowledgment ack = new Acknowledgment() {
                        @Override
                        public void acknowledge() {
                            try {
                                consumer.acknowledge(msg);
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void negativeAcknowledge() {
                            consumer.negativeAcknowledge(msg);
                        }
                    };
                    try {
                        handler.handle(event, ack);
                    } catch (RuntimeException e) {
                        consumer.negativeAcknowledge(msg);
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        });
    }

    @Override
    public void stop() {
        running.set(false);
        try {
            if (consumer != null) {
                consumer.close();
            }
            client.close();
        } catch (Exception ignored) {
        }
        if (loop != null) {
            loop.interrupt();
        }
    }
}
