package com.cim.mq.spi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import com.cim.mq.core.exception.MqException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * provider 注册与选择中心。
 *
 * <p>启动时经 {@link ServiceLoader} 自动发现 classpath 上所有 {@code MqProvider}
 * （见 {@code META-INF/services/com.cim.mq.spi.MqProvider}），也可通过
 * {@link #register(MqProvider)} 显式注册。{@link #getProvider(String)} 按
 * {@code cim.mq.type} 返回对应实现。</p>
 */
public final class MqProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(MqProviderFactory.class);

    private final Map<String, MqProvider> registry = new LinkedHashMap<>();

    public MqProviderFactory() {
        autoDiscover();
    }

    /** 扫描 ServiceLoader 登记的 provider。 */
    private void autoDiscover() {
        ServiceLoader<MqProvider> loader = ServiceLoader.load(MqProvider.class);
        for (java.util.Iterator<MqProvider> it = loader.iterator(); it.hasNext();) {
            try {
                register(it.next());
            } catch (java.util.ServiceConfigurationError e) {
                log.warn("跳过不可用的 MqProvider（缺少对应 MQ 客户端依赖？）: {}", e.getMessage());
            }
        }
    }

    /** 显式注册（覆盖同名）。 */
    public void register(MqProvider provider) {
        MqProvider prev = registry.putIfAbsent(provider.type(), provider);
        if (prev != null && prev != provider) {
            log.warn("MqProvider 类型 [{}] 重复注册，保留首次实现 [{}]", provider.type(), prev.getClass().getName());
        } else {
            log.info("已登记 MqProvider [{}] -> {}", provider.type(), provider.getClass().getName());
        }
    }

    /** 已注册类型列表（调试用）。 */
    public List<String> types() {
        return new ArrayList<>(registry.keySet());
    }

    /**
     * 按类型取 provider；不存在抛异常。
     */
    public MqProvider getProvider(String type) {
        MqProvider p = registry.get(type);
        if (p == null) {
            throw new MqException("未找到类型为 [" + type + "] 的 MqProvider，已注册：" + types()
                    + "。请确认引入了对应 provider 依赖并登记到 META-INF/services。");
        }
        return p;
    }
}
