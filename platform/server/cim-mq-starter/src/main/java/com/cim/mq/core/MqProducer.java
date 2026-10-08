package com.cim.mq.core;

import com.cim.mq.core.exception.MqSendException;

/**
 * 生产者契约（供应商中立）。
 *
 * <p>实现由具体 provider 提供（Kafka / Pulsar / RabbitMQ）。{@code routingKey}
 * 用于确定分区/队列路由：有序场景传入 {@code aggregateId}，无序场景可传 {@code null}。</p>
 */
public interface MqProducer {

    /**
     * 投递一条集成事件。
     *
     * @param event      事件信封
     * @param routingKey 路由键（有序场景为 aggregateId；无序可 null）
     * @return 投递结果
     * @throws MqSendException broker 不可达 / 未确认 / 超时
     */
    SendResult send(IntegrationEvent event, String routingKey) throws MqSendException;

    /** 释放底层资源。 */
    void close();
}
