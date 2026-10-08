package com.cim.mq.core;

/**
 * 管理契约（供应商中立）：主题/队列/死信声明与存活探针。
 *
 * <p>用于启动期确保 topic/queue 与死信队列存在，避免首条消息因目标不存在而丢失。</p>
 */
public interface MqAdmin {

    /** 声明主题/队列（幂等）。 */
    void declareTopic(String topic);

    /** 声明对应死信队列（命名约定 {@code <topic>.DLQ}）。 */
    void declareDlq(String topic);

    /** 是否存在该主题/队列。 */
    boolean topicExists(String topic);

    /** 释放底层资源。 */
    void close();
}
