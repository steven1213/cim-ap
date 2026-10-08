package com.cim.mq.provider.pulsar;

import com.cim.mq.core.ConsumerOptions;
import com.cim.mq.core.MqAdmin;
import com.cim.mq.core.MqConsumer;
import com.cim.mq.core.MqProducer;
import com.cim.mq.spi.MqProvider;
import com.cim.mq.spi.MqProviderContext;
import com.cim.mq.spi.ProviderFeatures;

/**
 * Pulsar provider（类型标识 {@code pulsar}）。
 *
 * <p>能力：按 key 严格有序（{@code ORDERED_BY_KEY}，{@code Key_Shared} 订阅）、
 * 持久化（默认 durable）、死信（{@code .DLQ} 约定）。</p>
 */
public class PulsarMqProvider implements MqProvider {

    @Override
    public String type() {
        return "pulsar";
    }

    @Override
    public ProviderFeatures features() {
        return ProviderFeatures.of(
                ProviderFeatures.Feature.ORDERED_BY_KEY,
                ProviderFeatures.Feature.DEAD_LETTER);
    }

    @Override
    public MqProducer createProducer(MqProviderContext ctx) {
        return new PulsarMqProducer(ctx);
    }

    @Override
    public MqConsumer createConsumer(MqProviderContext ctx, ConsumerOptions options) {
        return new PulsarMqConsumer(ctx, options);
    }

    @Override
    public MqAdmin createAdmin(MqProviderContext ctx) {
        return new PulsarMqAdmin(ctx);
    }
}
