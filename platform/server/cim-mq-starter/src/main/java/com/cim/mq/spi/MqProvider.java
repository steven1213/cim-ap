package com.cim.mq.spi;

import com.cim.mq.core.ConsumerOptions;
import com.cim.mq.core.MqAdmin;
import com.cim.mq.core.MqConsumer;
import com.cim.mq.core.MqProducer;

/**
 * ★ 扩展点（新增 MQ 的唯一入口）。
 *
 * <p>要支持一种新的消息中间件（RocketMQ / ActiveMQ / Redis Streams / AWS SQS …），
 * 只需在 {@code com.cim.mq.provider.<x>} 下实现本接口（含 Producer / Consumer / Admin 三个适配），
 * 并把该实现登记到 {@code META-INF/services/com.cim.mq.spi.MqProvider}
 * （或经 {@link MqProviderFactory#register(MqProvider)} 注册）。核心 {@code core} 代码无需改动。</p>
 *
 * <p>实现方职责：把自家概念（topic / partition / queue / exchange、ack 模式、有序语义）
 * 翻译为 {@code core} 的中立契约，并落实「不丢 / 不重 / 有序」相关语义
 * （持久化、确认机制、按 key 路由、幂等发送等）。</p>
 */
public interface MqProvider {

    /**
     * provider 类型标识，与应用配置 {@code cim.mq.type} 对应（如 kafka / pulsar / rabbit）。
     */
    String type();

    /**
     * 声明本 provider 真实具备的能力，框架据此设默认值与配置期校验。
     */
    ProviderFeatures features();

    /**
     * 创建生产者。
     */
    MqProducer createProducer(MqProviderContext ctx);

    /**
     * 创建消费者（按 options 订阅 topic + group）。
     */
    MqConsumer createConsumer(MqProviderContext ctx, ConsumerOptions options);

    /**
     * 创建管理端（声明 topic / 死信队列）。
     */
    MqAdmin createAdmin(MqProviderContext ctx);
}
