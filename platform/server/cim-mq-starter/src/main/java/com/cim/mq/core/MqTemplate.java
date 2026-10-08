package com.cim.mq.core;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.cim.mq.autoconfigure.CimMqProperties;
import com.cim.mq.idempotent.IdempotencyStore;
import com.cim.mq.observability.MqTraceInterceptor;
import com.cim.mq.resilience.DeadLetterPolicy;
import com.cim.mq.resilience.RetryPolicy;
import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.spi.MqProvider;
import com.cim.mq.spi.MqProviderContext;

/**
 * 应用侧唯一门面（供应商中立）。
 *
 * <p>业务代码只依赖本类与 {@link IntegrationEvent}：{@link #send} / {@link #sendOrdered}
 * 投递，{@link #registerListener} 订阅。底层 provider 对业务透明，切换 MQ 不改业务代码。</p>
 */
public class MqTemplate {

    private final MqProvider provider;
    private final MqProviderContext ctx;
    private final EventSerializer serializer;
    private final IdempotencyStore idempotency;
    private final RetryPolicy consumerRetry;
    private final DeadLetterPolicy deadLetter;
    private final Duration idemTtl;

    private volatile MqProducer producer;
    private volatile MqAdmin admin;
    private final List<MqConsumer> consumers = new CopyOnWriteArrayList<>();

    public MqTemplate(MqProvider provider, MqProviderContext ctx, EventSerializer serializer,
                      IdempotencyStore idempotency, RetryPolicy consumerRetry,
                      DeadLetterPolicy deadLetter, Duration idemTtl) {
        this.provider = provider;
        this.ctx = ctx;
        this.serializer = serializer;
        this.idempotency = idempotency;
        this.consumerRetry = consumerRetry;
        this.deadLetter = deadLetter;
        this.idemTtl = idemTtl;
    }

    /** 无序投递（默认以 aggregateId 路由，保证同聚合落到同分区/队列）。 */
    public SendResult send(IntegrationEvent event) {
        return doSend(event, event.aggregateId());
    }

    /** 有序投递：以 key（通常 aggregateId）保证该 key 内顺序。 */
    public SendResult sendOrdered(IntegrationEvent event, String key) {
        return doSend(event, key);
    }

    private SendResult doSend(IntegrationEvent event, String key) {
        MqTraceInterceptor.propagateTo(event);
        return producer().send(event, key);
    }

    /** 确保 topic/死信队列存在。 */
    public void declareTopic(String topic) {
        admin().declareTopic(topic);
        admin().declareDlq(topic);
    }

    /** 订阅主题（应用启动期调用）。 */
    public void registerListener(String topic, String group, MessageHandler handler) {
        registerListener(topic, group, handler, new ConsumerOptions(topic, group));
    }

    public void registerListener(String topic, String group, MessageHandler handler, ConsumerOptions options) {
        declareTopic(options.topic()); // 启动期确保 topic / 死信队列存在，避免首消息因目标缺失而丢失
        ResilientMessageHandler resilient = new ResilientMessageHandler(
                handler, idempotency, idemTtl, consumerRetry, deadLetter, producer());
        MqConsumer consumer = provider.createConsumer(ctx, options);
        consumers.add(consumer);
        consumer.start(resilient);
    }

    /** 释放所有资源（容器销毁时调用）。 */
    public void destroy() {
        consumers.forEach(MqConsumer::stop);
        consumers.clear();
        if (producer != null) {
            producer.close();
        }
        if (admin != null) {
            admin.close();
        }
    }

    /** 暴露底层生产者（供发件箱中继投递使用）。 */
    public MqProducer producer() {
        if (producer == null) {
            synchronized (this) {
                if (producer == null) {
                    producer = provider.createProducer(ctx);
                }
            }
        }
        return producer;
    }

    /** 暴露序列化器（供发件箱中继还原信封）。 */
    public EventSerializer serializer() {
        return serializer;
    }

    private MqAdmin admin() {
        if (admin == null) {
            synchronized (this) {
                if (admin == null) {
                    admin = provider.createAdmin(ctx);
                }
            }
        }
        return admin;
    }
}
