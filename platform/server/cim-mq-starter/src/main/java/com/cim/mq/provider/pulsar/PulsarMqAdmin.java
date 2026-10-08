package com.cim.mq.provider.pulsar;

import com.cim.mq.core.MqAdmin;
import com.cim.mq.spi.MqProviderContext;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;

/**
 * Pulsar 管理端：确保 topic / 死信 topic 存在。
 *
 * <p>通过创建并立即关闭一个 Producer 触发 broker 自动建 topic（Pulsar 默认开启
 * {@code allowAutoTopicCreation}）；生产环境建议改为显式声明以获得分区/留存策略控制。</p>
 */
public class PulsarMqAdmin implements MqAdmin {

    private final PulsarClient client;
    private final String dlqSuffix = ".DLQ";

    public PulsarMqAdmin(MqProviderContext ctx) {
        try {
            this.client = PulsarClient.builder()
                    .serviceUrl(ctx.properties().getPulsar().getServiceUrl())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("创建 Pulsar 客户端失败", e);
        }
    }

    private void ensure(String topic) {
        try (var p = client.newProducer(Schema.BYTES).topic(topic).create()) {
            // 创建即触发 broker 建 topic
        } catch (Exception e) {
            throw new IllegalStateException("确保 Pulsar topic 存在失败: " + topic, e);
        }
    }

    @Override
    public void declareTopic(String topic) {
        ensure(topic);
    }

    @Override
    public void declareDlq(String topic) {
        ensure(topic.endsWith(dlqSuffix) ? topic : topic + dlqSuffix);
    }

    @Override
    public boolean topicExists(String topic) {
        try (var p = client.newProducer(Schema.BYTES).topic(topic).create()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        try {
            client.close();
        } catch (Exception ignored) {
        }
    }
}
