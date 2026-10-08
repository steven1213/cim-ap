package com.cim.mq.provider.rabbit;

import com.cim.mq.core.ConsumerOptions;
import com.cim.mq.core.MqAdmin;
import com.cim.mq.core.MqConsumer;
import com.cim.mq.core.MqProducer;
import com.cim.mq.spi.MqProvider;
import com.cim.mq.spi.MqProviderContext;
import com.cim.mq.spi.ProviderFeatures;

/**
 * RabbitMQ provider（类型标识 {@code rabbit}）。
 *
 * <p>能力：消息/队列持久化、原生死信（{@code DEAD_LETTER}）。单队列 FIFO 有序；
 * 需要 per-key 顺序时改用一致哈希交换（{@code x-consistent-hash}），由用户在
 * {@code cim.mq.rabbit.extras} 配置，provider 已预留扩展点。</p>
 */
public class RabbitMqProvider implements MqProvider {

    @Override
    public String type() {
        return "rabbit";
    }

    @Override
    public ProviderFeatures features() {
        return ProviderFeatures.of(ProviderFeatures.Feature.DEAD_LETTER);
    }

    @Override
    public MqProducer createProducer(MqProviderContext ctx) {
        return new RabbitMqProducer(ctx);
    }

    @Override
    public MqConsumer createConsumer(MqProviderContext ctx, ConsumerOptions options) {
        return new RabbitMqConsumer(ctx, options);
    }

    @Override
    public MqAdmin createAdmin(MqProviderContext ctx) {
        return new RabbitMqAdmin(ctx);
    }
}
