package com.cim.mq.spi;

import com.cim.mq.serialize.EventSerializer;
import com.cim.mq.autoconfigure.CimMqProperties;

/**
 * provider 构造上下文：把框架侧已解析的配置与公共组件交给具体 provider。
 *
 * <p>provider 不应直接读 Spring 环境，只从这里取所需内容，保持「核心零厂商 SDK、
 * 仅 provider 依赖厂商 SDK」的边界。</p>
 */
public class MqProviderContext {

    private final CimMqProperties properties;
    private final EventSerializer serializer;

    public MqProviderContext(CimMqProperties properties, EventSerializer serializer) {
        this.properties = properties;
        this.serializer = serializer;
    }

    public CimMqProperties properties() {
        return properties;
    }

    public EventSerializer serializer() {
        return serializer;
    }
}
