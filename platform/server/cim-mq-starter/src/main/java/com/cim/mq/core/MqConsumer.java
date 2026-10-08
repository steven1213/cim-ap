package com.cim.mq.core;

/**
 * 消费者契约（供应商中立）。
 *
 * <p>调用 {@link #start(MessageHandler)} 后，provider 在自有线程（或虚拟线程）中拉取消息，
 * 经框架包裹的 {@code MessageHandler}（含幂等/重试/死信）处理后手动 ack。</p>
 */
public interface MqConsumer {

    /** 启动消费，注册处理函数。 */
    void start(MessageHandler handler);

    /** 停止并释放资源。 */
    void stop();
}
