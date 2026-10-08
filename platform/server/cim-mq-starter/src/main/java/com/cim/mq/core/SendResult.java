package com.cim.mq.core;

/** 单次投递结果（由各 provider 回填，用于可观测与日志）。 */
public class SendResult {

    private final String topic;
    private final String messageId;
    private String partitionOrQueue;
    private Long offset;

    public SendResult(String topic, String messageId) {
        this.topic = topic;
        this.messageId = messageId;
    }

    public static SendResult of(String topic, String messageId) {
        return new SendResult(topic, messageId);
    }

    public SendResult partitionOrQueue(String v) {
        this.partitionOrQueue = v;
        return this;
    }

    public SendResult offset(Long v) {
        this.offset = v;
        return this;
    }

    public String topic() {
        return topic;
    }

    public String messageId() {
        return messageId;
    }

    public String partitionOrQueue() {
        return partitionOrQueue;
    }

    public Long offset() {
        return offset;
    }
}
