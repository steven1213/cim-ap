package com.cim.mq.provider.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MqProducer;
import com.cim.mq.core.SendResult;
import com.cim.mq.core.exception.MqSendException;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.spi.MqProviderContext;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Kafka 生产者适配。
 *
 * <p>落实「不丢」：{@code acks=all} + 幂等生产者（{@code enable.idempotence=true}），
 * 发送同步等待 broker 确认，超时即抛 {@link MqSendException} 触发上层重试。
 * 路由键 = aggregateId，保证同聚合进入同分区 → 分区内有序。</p>
 */
public class KafkaMqProducer implements MqProducer {

    private final KafkaProducer<String, byte[]> producer;
    private final EventSerializer serializer;
    private final String defaultTopic;
    private final String bootstrapServers;
    private final Set<String> declared = ConcurrentHashMap.newKeySet();
    private volatile AdminClient admin;

    public KafkaMqProducer(MqProviderContext ctx) {
        com.cim.mq.autoconfigure.CimMqProperties.Kafka k = ctx.properties().getKafka();
        java.util.Properties p = new java.util.Properties();
        p.put("bootstrap.servers", k.getBootstrapServers());
        p.put("key.serializer", StringSerializer.class.getName());
        p.put("value.serializer", ByteArraySerializer.class.getName());
        p.put("acks", k.getAcks());
        p.put("retries", k.getRetries());
        p.put("enable.idempotence", k.isEnableIdempotence());
        p.put("max.in.flight.requests.per.connection", 5);
        k.getExtras().forEach(p::put);
        this.producer = new KafkaProducer<>(p);
        this.serializer = ctx.serializer();
        this.defaultTopic = ctx.properties().getDefaultTopic();
        this.bootstrapServers = k.getBootstrapServers();
    }

    @Override
    public SendResult send(IntegrationEvent event, String routingKey) throws MqSendException {
        String topic = event.topic() != null ? event.topic() : defaultTopic;
        ensureTopic(topic); // 发送前确保 topic 存在（涵盖发件箱中继等无本地消费者场景）
        ProducerRecord<String, byte[]> rec =
                new ProducerRecord<>(topic, routingKey, serializer.serialize(event));
        try {
            RecordMetadata meta = producer.send(rec).get(30, TimeUnit.SECONDS);
            return SendResult.of(topic, event.id())
                    .partitionOrQueue(Integer.toString(meta.partition()))
                    .offset(meta.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MqSendException("Kafka 发送被中断: " + topic, e);
        } catch (Exception e) {
            throw new MqSendException("Kafka 发送失败: " + topic, e);
        }
    }

    @Override
    public void close() {
        producer.close(Duration.ofSeconds(5));
        if (admin != null) {
            admin.close();
        }
    }

    /** 惰性、幂等地确保 topic 存在（每个 topic 仅建一次，避免每条消息都打 admin）。 */
    private void ensureTopic(String topic) {
        if (declared.contains(topic)) {
            return;
        }
        synchronized (this) {
            if (declared.contains(topic)) {
                return;
            }
            if (admin == null) {
                java.util.Properties p = new java.util.Properties();
                p.put("bootstrap.servers", bootstrapServers);
                admin = AdminClient.create(p);
            }
            try {
                admin.createTopics(Collections.singletonList(new NewTopic(topic, 3, (short) 1))).all()
                        .get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw new IllegalStateException("创建 Kafka topic 失败: " + topic, e);
                }
            } catch (Exception e) {
                throw new IllegalStateException("创建 Kafka topic 失败: " + topic, e);
            }
            declared.add(topic);
        }
    }
}
