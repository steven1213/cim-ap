package com.cim.mq.resilience;

/**
 * 死信策略：定义最大重试次数与死信队列命名。
 *
 * <p>消费侧超过 {@link #maxAttempts} 仍失败，消息被转发到 {@link #dlqTopic(String)}，
 * 原始消息确认以避免无限重投；死信由人工/旁路补偿处理。</p>
 */
public class DeadLetterPolicy {

    private final int maxAttempts;
    private final String dlqSuffix;

    public DeadLetterPolicy(int maxAttempts) {
        this(maxAttempts, ".DLQ");
    }

    public DeadLetterPolicy(int maxAttempts, String dlqSuffix) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.dlqSuffix = dlqSuffix == null ? ".DLQ" : dlqSuffix;
    }

    public static DeadLetterPolicy of(int maxAttempts) {
        return new DeadLetterPolicy(maxAttempts, ".DLQ");
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** 死信主题名（约定 {@code <topic>.DLQ}）。 */
    public String dlqTopic(String topic) {
        return topic.endsWith(dlqSuffix) ? topic : topic + dlqSuffix;
    }
}
