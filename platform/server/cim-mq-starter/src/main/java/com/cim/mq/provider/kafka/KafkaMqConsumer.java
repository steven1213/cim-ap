package com.cim.mq.provider.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cim.mq.core.Acknowledgment;
import com.cim.mq.core.ConsumerOptions;
import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MessageHandler;
import com.cim.mq.core.MqConsumer;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.spi.MqProviderContext;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Kafka 消费者适配（手动 ack）。
 *
 * <p>采用 {@code enable.auto.commit=false}，业务处理成功后才同步提交位点（精确控制，避免「提前 ack 丢消息」）。
 * 在虚拟线程中 poll 循环；同分区内消息按顺序交付（配合分区有序）。</p>
 */
public class KafkaMqConsumer implements MqConsumer {

    private final KafkaConsumer<String, byte[]> consumer;
    private final EventSerializer serializer;
    private final ConsumerOptions options;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread loop;

    public KafkaMqConsumer(MqProviderContext ctx, ConsumerOptions options) {
        com.cim.mq.autoconfigure.CimMqProperties.Kafka k = ctx.properties().getKafka();
        java.util.Properties p = new java.util.Properties();
        p.put("bootstrap.servers", k.getBootstrapServers());
        p.put("group.id", options.group());
        p.put("key.deserializer", StringDeserializer.class.getName());
        p.put("value.deserializer", ByteArrayDeserializer.class.getName());
        p.put("enable.auto.commit", "false");
        p.put("auto.offset.reset", "earliest");
        p.put("isolation.level", "read_committed");
        k.getExtras().forEach(p::put);
        this.consumer = new KafkaConsumer<>(p);
        this.serializer = ctx.serializer();
        this.options = options;
    }

    @Override
    public void start(MessageHandler handler) {
        consumer.subscribe(Collections.singletonList(options.topic()));
        loop = Thread.ofVirtual().name("cim-mq-kafka-" + options.topic()).start(() -> {
            while (running.get()) {
                try {
                    var records = consumer.poll(Duration.ofMillis(500));
                    for (var rec : records) {
                        IntegrationEvent event = serializer.deserialize(rec.value());
                        Map<TopicPartition, OffsetAndMetadata> offsets = Collections.singletonMap(
                                new TopicPartition(rec.topic(), rec.partition()),
                                new OffsetAndMetadata(rec.offset() + 1));
                        final boolean[] acked = {false};
                        final boolean[] nacked = {false};
                        Acknowledgment ack = new Acknowledgment() {
                            @Override
                            public void acknowledge() {
                                acked[0] = true;
                                consumer.commitSync(offsets);
                            }

                            @Override
                            public void negativeAcknowledge() {
                                nacked[0] = true; // 不提交位点，broker 将重投
                            }
                        };
                        boolean threw = false;
                        try {
                            handler.handle(event, ack);
                        } catch (Exception e) {
                            threw = true; // 防御：用户 handler 抛异常按重投处理
                        }
                        // 关键：单条未确认（业务最终失败 / 死信转发失败）时，停止提交后续更高水位的位点，
                        // 避免「中间失败、后面成功提交更高 offset」导致中间消息被跳过而永久丢失（at-least-once 违规）。
                        if (nacked[0] || threw) {
                            break;
                        }
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
            consumer.close(Duration.ofSeconds(5));
        } catch (Exception ignored) {
        }
        if (loop != null) {
            loop.interrupt();
        }
    }
}
