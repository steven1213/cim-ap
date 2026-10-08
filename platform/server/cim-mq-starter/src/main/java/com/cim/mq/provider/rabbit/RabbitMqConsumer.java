package com.cim.mq.provider.rabbit;

import java.util.concurrent.atomic.AtomicBoolean;

import com.cim.mq.core.Acknowledgment;
import com.cim.mq.core.ConsumerOptions;
import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.core.MessageHandler;
import com.cim.mq.core.MqConsumer;
import com.cim.mq.core.exception.MqConsumeException;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.spi.MqProviderContext;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

/**
 * RabbitMQ 消费者适配（手动 ack + 原生死信交换）。
 *
 * <p>队列/消息持久化 + {@code autoAck=false}；处理成功才 {@code basicAck}。失败
 * {@code basicNack(requeue=false)} 经 {@code x-dead-letter-exchange} 落入 {@code .DLQ} 队列。
 * 单队列内 FIFO 有序（需 per-key 顺序时使用一致哈希交换，见 design.md §2.6.2）。</p>
 */
public class RabbitMqConsumer implements MqConsumer {

    private final Connection connection;
    private final Channel channel;
    private final EventSerializer serializer;
    private final MqProviderContext ctx;
    private final ConsumerOptions options;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private String consumerTag;

    public RabbitMqConsumer(MqProviderContext ctx, ConsumerOptions options) {
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
        } catch (Exception e) {
            throw new IllegalStateException("创建 RabbitMQ 连接失败", e);
        }
        this.serializer = ctx.serializer();
        this.ctx = ctx;
        this.options = options;
    }

    @Override
    public void start(MessageHandler handler) {
        try {
            String exchange = ctx.properties().getRabbit().getExchange();
            String queue = options.topic();
            RabbitTopology.declare(channel, exchange, queue);

            DeliverCallback deliver = (tag, delivery) -> {
                if (!running.get()) {
                    return;
                }
                try {
                    IntegrationEvent event = serializer.deserialize(delivery.getBody());
                    Acknowledgment ack = new Acknowledgment() {
                        @Override
                        public void acknowledge() {
                            try {
                                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void negativeAcknowledge() {
                            try {
                                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                            } catch (Exception ignored) {
                            }
                        }
                    };
                    try {
                        handler.handle(event, ack);
                    } catch (RuntimeException e) {
                        ack.negativeAcknowledge();
                    }
                } catch (Exception e) {
                    try {
                        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                    } catch (Exception ignored) {
                    }
                }
            };
            this.consumerTag = channel.basicConsume(queue, false, deliver, ct -> {
            });
        } catch (Exception e) {
            throw new IllegalStateException("订阅 RabbitMQ 队列失败: " + options.topic(), e);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        try {
            if (consumerTag != null) {
                channel.basicCancel(consumerTag);
            }
            channel.close();
            connection.close();
        } catch (Exception ignored) {
        }
    }
}
