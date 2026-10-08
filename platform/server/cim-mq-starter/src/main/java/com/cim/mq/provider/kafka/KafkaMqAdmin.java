package com.cim.mq.provider.kafka;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.cim.mq.core.MqAdmin;
import com.cim.mq.spi.MqProviderContext;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;

/**
 * Kafka 管理端：确保 topic / 死信 topic 存在（启动期调用，避免首条消息因目标缺失而丢失）。
 */
public class KafkaMqAdmin implements MqAdmin {

    private final AdminClient admin;
    private final String dlqSuffix = ".DLQ";

    public KafkaMqAdmin(MqProviderContext ctx) {
        java.util.Properties p = new java.util.Properties();
        p.put("bootstrap.servers", ctx.properties().getKafka().getBootstrapServers());
        this.admin = AdminClient.create(p);
    }

    @Override
    public void declareTopic(String topic) {
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
    }

    @Override
    public void declareDlq(String topic) {
        declareTopic(topic.endsWith(dlqSuffix) ? topic : topic + dlqSuffix);
    }

    @Override
    public boolean topicExists(String topic) {
        try {
            Set<String> names = admin.listTopics(new ListTopicsOptions().timeoutMs(5000)).names().get(5, TimeUnit.SECONDS);
            return names.contains(topic);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        admin.close();
    }
}
