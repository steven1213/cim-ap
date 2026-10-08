package com.cim.mq.core;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

/**
 * 集成事件信封（跨服务，经发件箱 + MQ 投递）。
 *
 * <p>与 {@code cim-core} 的进程内 {@code DomainEvent} 区分：本对象是「线缆格式」，
 * 自带 {@code headers}（traceId / tenantId / userId）以在全链路透传；{@code payload}
 * 为 JSON 字符串，{@code payloadType} 记录反序列化目标类型。</p>
 *
 * <p>投递语义由 {@code cim.mq} 框架保障：at-least-once 投递 + 消费幂等（见 §12/§13/§16）。</p>
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class IntegrationEvent implements Serializable {

    private String id;
    private String topic;
    private String type;
    private String aggregateType;
    private String aggregateId;
    private long version;
    private String payloadType;
    private String payload;
    private Map<String, String> headers = new LinkedHashMap<>();
    private Instant occurredAt;

    public IntegrationEvent() {
    }

    private IntegrationEvent(Builder b) {
        this.id = b.id != null ? b.id : UUID.randomUUID().toString().replace("-", "");
        this.topic = b.topic;
        this.type = b.type;
        this.aggregateType = b.aggregateType;
        this.aggregateId = b.aggregateId;
        this.version = b.version;
        this.payloadType = b.payloadType;
        this.payload = b.payload;
        this.headers = b.headers != null ? b.headers : new LinkedHashMap<>();
        this.occurredAt = b.occurredAt != null ? b.occurredAt : Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String id() {
        return id;
    }

    public String topic() {
        return topic;
    }

    public String type() {
        return type;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    /** 业务版本号，配合 {@code aggregateId} 做乐观去重（状态类事件）。 */
    public long version() {
        return version;
    }

    public String payloadType() {
        return payloadType;
    }

    public String payload() {
        return payload;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public String header(String key) {
        return headers.get(key);
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setPayloadType(String payloadType) {
        this.payloadType = payloadType;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setHeader(String key, String value) {
        this.headers.put(key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntegrationEvent)) {
            return false;
        }
        return id != null && id.equals(((IntegrationEvent) o).id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    public static final class Builder {
        private String id;
        private String topic;
        private String type;
        private String aggregateType;
        private String aggregateId;
        private long version;
        private String payloadType;
        private String payload;
        private Map<String, String> headers;
        private Instant occurredAt;

        public Builder id(String v) {
            this.id = v;
            return this;
        }

        public Builder topic(String v) {
            this.topic = v;
            return this;
        }

        public Builder type(String v) {
            this.type = v;
            return this;
        }

        public Builder aggregateType(String v) {
            this.aggregateType = v;
            return this;
        }

        public Builder aggregateId(String v) {
            this.aggregateId = v;
            return this;
        }

        public Builder version(long v) {
            this.version = v;
            return this;
        }

        public Builder payloadType(String v) {
            this.payloadType = v;
            return this;
        }

        public Builder payload(String v) {
            this.payload = v;
            return this;
        }

        public Builder headers(Map<String, String> v) {
            this.headers = v;
            return this;
        }

        public Builder occurredAt(Instant v) {
            this.occurredAt = v;
            return this;
        }

        public IntegrationEvent build() {
            return new IntegrationEvent(this);
        }
    }
}
