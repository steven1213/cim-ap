package com.cim.mq.serialize;

import java.nio.charset.StandardCharsets;

import com.cim.mq.core.IntegrationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 基于 Jackson 的事件序列化（默认实现）。
 *
 * <p>信封自身为自描述结构（payload 以 JSON 字符串存放，{@code payloadType} 记录目标类型），
 * 因此反序列化无需预先知道业务类型即可还原信封。</p>
 */
public class JacksonEventSerializer implements EventSerializer {

    private final ObjectMapper mapper;

    public JacksonEventSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public byte[] serialize(IntegrationEvent event) {
        try {
            return mapper.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 IntegrationEvent 失败: " + event.id(), e);
        }
    }

    @Override
    public IntegrationEvent deserialize(byte[] bytes) {
        try {
            return mapper.readValue(bytes, IntegrationEvent.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 IntegrationEvent 失败", e);
        }
    }

    /** 把业务对象序列化进信封 payload（设置 payloadType 便于回放）。 */
    public <T> void embedPayload(IntegrationEvent event, T payload) {
        try {
            event.setPayloadType(payload.getClass().getName());
            event.setPayload(new String(mapper.writeValueAsBytes(payload), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("嵌入 payload 失败", e);
        }
    }

    /** 从信封 payload 还原业务对象。 */
    public <T> T extractPayload(IntegrationEvent event, Class<T> type) {
        try {
            return mapper.readValue(event.payload(), type);
        } catch (Exception e) {
            throw new IllegalStateException("还原 payload 失败: " + type.getName(), e);
        }
    }
}
