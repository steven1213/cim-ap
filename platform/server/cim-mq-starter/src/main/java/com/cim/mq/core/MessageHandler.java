package com.cim.mq.core;

import com.cim.mq.core.exception.MqConsumeException;

/**
 * 消息处理器（消费侧回调）。
 *
 * <p>实现方完成业务处理；处理成功调用 {@code ack.acknowledge()}，失败调用
 * {@code ack.negativeAcknowledge()}。框架会在外层包裹幂等过滤、重试与死信。</p>
 */
@FunctionalInterface
public interface MessageHandler {

    void handle(IntegrationEvent event, Acknowledgment ack) throws MqConsumeException;
}
