package com.cim.obs.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;

/**
 * 业务指标门面（design.md §2.8 / T4.4）。
 *
 * <p>统一封装 {@link MeterRegistry}，自动加 {@code cim.} 前缀与公共标签，避免业务代码
 * 直接耦合 Micrometer API。计数器/计时器可直接落地到 Prometheus。</p>
 */
public class MetricsRegistry {

    private final MeterRegistry registry;
    private final String prefix;

    public MetricsRegistry(MeterRegistry registry, String prefix) {
        this.registry = Objects.requireNonNull(registry, "MeterRegistry required");
        this.prefix = (prefix == null || prefix.isBlank()) ? "cim." : prefix;
    }

    /** 计数器（如请求数、错误数）。标签为交替的 key/value。 */
    public Counter counter(String name, String... tags) {
        return registry.counter(prefix + name, tags);
    }

    /** 计时器（如接口耗时）。 */
    public Timer timer(String name, String... tags) {
        return registry.timer(prefix + name, tags);
    }

    /** 即时量（如队列长度）。调用方需持有返回对象引用，避免被 GC 回收。 */
    public <T extends Number> T gauge(String name, T value, String... tags) {
        return registry.gauge(prefix + name, Tags.of(tags), value, Number::doubleValue);
    }
}
