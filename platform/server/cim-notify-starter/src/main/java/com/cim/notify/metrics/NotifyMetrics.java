package com.cim.notify.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * 实时推送内部指标门面（自动加 {@code cim.} 前缀）。
 *
 * <p><b>为什么不用 {@code cim-obs-starter} 的 MetricsRegistry</b>：本 starter 是纯传输层
 * （见 design.md §1.1「starter 之间禁止横向依赖」），为一个计数器把 actuator + prometheus
 * 整个拖进依赖闭包不值得。这里只依赖 micrometer 的 <b>接口</b> {@link MeterRegistry}，
 * 宿主装了 {@code cim-obs-starter} 时自动联通（obs 产出的就是 MeterRegistry 实例），
 * 没装或没有该 Bean 时退化为 no-op，零配置。</p>
 */
public final class NotifyMetrics {

    private final MeterRegistry registry;
    private final String prefix;

    public NotifyMetrics(MeterRegistry registry) {
        this(registry, "cim.");
    }

    public NotifyMetrics(MeterRegistry registry, String prefix) {
        this.registry = registry;
        this.prefix = (prefix == null || prefix.isBlank()) ? "cim." : prefix;
    }

    /** 是否真正在采集（宿主是否提供 MeterRegistry）。 */
    public boolean isEnabled() {
        return registry != null;
    }

    /** 计数 +1。标签为交替的 key/value。 */
    public void increment(String name, String... tags) {
        if (registry == null) {
            return;
        }
        registry.counter(prefix + name, tags).increment();
    }

    /** 注册即时量（同名同标签幂等）。调用方需持有 value 引用，避免被 GC 回收。 */
    public <T extends Number> void gauge(String name, T value, String... tags) {
        if (registry == null) {
            return;
        }
        registry.gauge(prefix + name, Tags.of(tags), value, Number::doubleValue);
    }
}
