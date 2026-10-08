package com.cim.mq.core;

/** 消费者订阅选项（供应商中立）。 */
public class ConsumerOptions {

    private final String topic;
    private final String group;
    private int concurrency = 1;
    private int maxAttempts = 3;

    public ConsumerOptions(String topic, String group) {
        this.topic = topic;
        this.group = group;
    }

    public static ConsumerOptions of(String topic, String group) {
        return new ConsumerOptions(topic, group);
    }

    public ConsumerOptions concurrency(int v) {
        this.concurrency = v;
        return this;
    }

    public ConsumerOptions maxAttempts(int v) {
        this.maxAttempts = v;
        return this;
    }

    public String topic() {
        return topic;
    }

    public String group() {
        return group;
    }

    public int concurrency() {
        return concurrency;
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
