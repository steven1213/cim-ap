package com.cim.mq.provider.rabbit;

import java.util.HashMap;
import java.util.Map;

import com.cim.mq.core.MqAdmin;
import com.cim.mq.spi.MqProviderContext;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * RabbitMQ 管理端：复用 {@link RabbitTopology} 声明 topic 交换、主队列（含死信参数）与死信队列。
 *
 * <p>与运行期 producer / consumer 使用<b>完全相同</b>的拓扑（同一交换名 + 同一 DLX 参数），
 * 杜绝重复 queueDeclare 触发 {@code inequivalent arguments}。这也是 {@link MqTemplate#registerListener}
 * 启动期声明 topic 的落点。</p>
 */
public class RabbitMqAdmin implements MqAdmin {

    private final Connection connection;
    private final Channel channel;
    private final String exchange;
    private final String dlqSuffix;

    public RabbitMqAdmin(MqProviderContext ctx) {
        com.cim.mq.autoconfigure.CimMqProperties.Rabbit r = ctx.properties().getRabbit();
        this.exchange = r.getExchange();
        this.dlqSuffix = RabbitTopology.DLQ_SUFFIX;
        ConnectionFactory factory = new ConnectionFactory();
        String[] hp = r.getAddresses().split(",")[0].split(":");
        try {
            factory.setHost(hp[0]);
            factory.setPort(hp.length > 1 ? Integer.parseInt(hp[1]) : 5672);
            factory.setUsername(r.getUsername());
            factory.setPassword(r.getPassword());
            factory.setVirtualHost(r.getVirtualHost());
            this.connection = factory.newConnection();
            this.channel = connection.createChannel();
        } catch (Exception e) {
            throw new IllegalStateException("创建 RabbitMQ 连接失败", e);
        }
    }

    @Override
    public void declareTopic(String topic) {
        try {
            RabbitTopology.declare(channel, exchange, topic);
        } catch (Exception e) {
            throw new IllegalStateException("声明 RabbitMQ 拓扑失败: " + topic, e);
        }
    }

    @Override
    public void declareDlq(String topic) {
        // 死信队列已由 declareTopic（RabbitTopology.declare）一并声明，此处仅幂等确保存在
        try {
            String dlqKey = topic.endsWith(dlqSuffix) ? topic : topic + dlqSuffix;
            channel.queueDeclare(dlqKey, true, false, false, null);
        } catch (Exception e) {
            throw new IllegalStateException("声明 RabbitMQ 死信队列失败: " + topic, e);
        }
    }

    @Override
    public boolean topicExists(String topic) {
        try {
            channel.queueDeclarePassive(topic);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void close() {
        try {
            channel.close();
            connection.close();
        } catch (Exception ignored) {
        }
    }
}
