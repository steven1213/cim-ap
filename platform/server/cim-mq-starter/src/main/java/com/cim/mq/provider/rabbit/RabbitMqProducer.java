package com.cim.mq.provider.rabbit;

import java.util.concurrent.TimeUnit;

import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MqProducer;
import com.cim.mq.core.SendResult;
import com.cim.mq.core.exception.MqSendException;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.spi.MqProviderContext;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * RabbitMQ 生产者适配。
 *
 * <p>落实「不丢」：队列/消息持久化（{@code deliveryMode=2}）+ {@code publisher confirm}
 * 同步等待 broker 确认；失败抛 {@link MqSendException} 触发重试。单队列内 FIFO 有序。</p>
 */
public class RabbitMqProducer implements MqProducer {

    private final Connection connection;
    private final Channel channel;
    private final EventSerializer serializer;
    private final String exchange;
    private final String defaultTopic;

    public RabbitMqProducer(MqProviderContext ctx) {
        com.cim.mq.autoconfigure.CimMqProperties.Rabbit r = ctx.properties().getRabbit();
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
            this.channel.confirmSelect();
        } catch (Exception e) {
            throw new MqSendException("创建 RabbitMQ 连接失败", e);
        }
        this.serializer = ctx.serializer();
        this.exchange = r.getExchange();
        this.defaultTopic = ctx.properties().getDefaultTopic();
    }

    @Override
    public synchronized SendResult send(IntegrationEvent event, String routingKey) throws MqSendException {
        String topic = event.topic() != null ? event.topic() : defaultTopic;
        try {
            RabbitTopology.declare(channel, exchange, topic);
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().deliveryMode(2).build();
            channel.basicPublish(exchange, topic, props, serializer.serialize(event));
            boolean confirmed = channel.waitForConfirms(30_000);
            if (!confirmed) {
                throw new MqSendException("RabbitMQ 未确认: " + topic);
            }
            return SendResult.of(topic, event.id()).partitionOrQueue(topic);
        } catch (MqSendException e) {
            throw e;
        } catch (Exception e) {
            throw new MqSendException("RabbitMQ 发送失败: " + topic, e);
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
