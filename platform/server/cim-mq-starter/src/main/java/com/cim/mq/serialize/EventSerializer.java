package com.cim.mq.serialize;

import com.cim.mq.core.IntegrationEvent;

/**
 * 事件序列化契约（线缆格式）。
 *
 * <p>默认 JSON 实现；如需跨语言兼容/高性能可替换为 AVRO / Protobuf 实现，
 * 仅需实现本接口并在自动装配处替换 bean。</p>
 */
public interface EventSerializer {

    /** 信封 → 字节（用于 broker 传输 / outbox 存储）。 */
    byte[] serialize(IntegrationEvent event);

    /** 字节 → 信封。 */
    IntegrationEvent deserialize(byte[] bytes);
}
