package com.cim.mq.provider.rabbit;

import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.Channel;

/**
 * RabbitMQ 拓扑声明（producer / consumer 共用，确保双方声明的队列参数完全一致，
 * 避免重复 queueDeclare 触发 {@code inequivalent arguments} 错误）。
 *
 * <p>约定：topic 交换（{@code cim-mq-exchange}）+ 同名持久化队列；队列挂原生死信，
 * 死信经同一交换以 {@code <topic>.DLQ} 路由键落入 {@code <topic>.DLQ} 队列。</p>
 */
final class RabbitTopology {

    static final String DLQ_SUFFIX = ".DLQ";

    private RabbitTopology() {
    }

    static void declare(Channel channel, String exchange, String topic) throws Exception {
        channel.exchangeDeclare(exchange, "topic", true);

        String dlqKey = topic + DLQ_SUFFIX;
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", exchange);
        args.put("x-dead-letter-routing-key", dlqKey);

        channel.queueDeclare(topic, true, false, false, args);
        channel.queueBind(topic, exchange, topic);

        channel.queueDeclare(dlqKey, true, false, false, null);
        channel.queueBind(dlqKey, exchange, dlqKey);
    }
}
