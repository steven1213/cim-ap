package com.cim.mq.core;

/**
 * 消息确认句柄（消费侧）。
 *
 * <p>框架采用<b>手动 ack</b>语义：业务处理成功后才 {@link #acknowledge()}，
 * 失败/需重试时 {@link #negativeAcknowledge()}。配合 at-least-once 投递，
 * 处理成功前不确认即可避免「消费端丢消息」。</p>
 */
public interface Acknowledgment {

    /** 处理成功，提交位点（broker 不再重投）。 */
    void acknowledge();

    /** 处理失败，请求重投（或进入重试/死信流程）。 */
    void negativeAcknowledge();

    /** 空实现（测试 / 无需确认场景）。 */
    Acknowledgment NOOP = new Acknowledgment() {
        @Override
        public void acknowledge() {
        }

        @Override
        public void negativeAcknowledge() {
        }
    };
}
