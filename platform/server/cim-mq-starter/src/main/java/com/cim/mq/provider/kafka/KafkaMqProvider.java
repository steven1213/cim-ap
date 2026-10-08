package com.cim.mq.provider.kafka;

import com.cim.mq.core.ConsumerOptions;
import com.cim.mq.core.MqAdmin;
import com.cim.mq.core.MqConsumer;
import com.cim.mq.core.MqProducer;
import com.cim.mq.spi.MqProvider;
import com.cim.mq.spi.MqProviderContext;
import com.cim.mq.spi.ProviderFeatures;

/**
 * Kafka provider（类型标识 {@code kafka}）。
 *
 * <p>能力：按 key 分区有序（{@code ORDERED_BY_KEY}）、幂等生产者（{@code PRODUCER_IDEMPOTENT}）、
 * 死信（{@code DEAD_LETTER}，由框架侧 {@code .DLQ} 约定实现）。</p>
 */
public class KafkaMqProvider implements MqProvider {

    @Override
    public String type() {
        return "kafka";
    }

    @Override
    public ProviderFeatures features() {
        return ProviderFeatures.of(
                ProviderFeatures.Feature.ORDERED_BY_KEY,
                ProviderFeatures.Feature.PRODUCER_IDEMPOTENT,
                ProviderFeatures.Feature.DEAD_LETTER);
    }

    @Override
    public MqProducer createProducer(MqProviderContext ctx) {
        return new KafkaMqProducer(ctx);
    }

    @Override
    public MqConsumer createConsumer(MqProviderContext ctx, ConsumerOptions options) {
        return new KafkaMqConsumer(ctx, options);
    }

    @Override
    public MqAdmin createAdmin(MqProviderContext ctx) {
        return new KafkaMqAdmin(ctx);
    }
}
