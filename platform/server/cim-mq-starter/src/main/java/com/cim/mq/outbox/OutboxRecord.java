package com.cim.mq.outbox;

import java.time.Instant;

/**
 * 发件箱记录（与业务表同事务写入，保证「业务成功 ⇔ 消息记录存在」）。
 *
 * <p>中继读取 PENDING 记录并投递到 MQ，成功后标记 SENT/删除；失败按退避更新
 * nextRetryAt。这是「不丢消息」的第一道保险（README §12）。</p>
 */
public class OutboxRecord {

    public enum Status { PENDING, SENDING, SENT, FAILED }

    String id;
    String aggregateType;
    String aggregateId;
    String eventType;
    String topic;
    byte[] payload;
    Status status = Status.PENDING;
    int attemptCount = 0;
    Instant createTime = Instant.now();
    Instant nextRetryAt = Instant.now();
    String lastError;

    public OutboxRecord() {
    }

    public OutboxRecord(String id, String aggregateType, String aggregateId,
                        String eventType, String topic, byte[] payload) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
    }

    public String id() {
        return id;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public String topic() {
        return topic;
    }

    public byte[] payload() {
        return payload;
    }

    public Status status() {
        return status;
    }

    public void status(Status s) {
        this.status = s;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public void attemptCount(int v) {
        this.attemptCount = v;
    }

    public Instant createTime() {
        return createTime;
    }

    public void createTime(Instant v) {
        this.createTime = v;
    }

    public Instant nextRetryAt() {
        return nextRetryAt;
    }

    public void nextRetryAt(Instant v) {
        this.nextRetryAt = v;
    }

    public String lastError() {
        return lastError;
    }

    public void lastError(String v) {
        this.lastError = v;
    }
}
