package com.cim.obs.trace;

import com.cim.obs.config.CimObsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 链路桥接配置（design.md §2.8 / T4.4）。
 *
 * <p>仅当 {@code micrometer-tracing} + OTel bridge 在 classpath 时才激活；离线（沙箱无链路依赖）
 * 时本配置整体不加载，链路由 {@code cim-spring-support} 的 {@code TraceContext}(MDC traceId) 兜底。
 * 真实链路注入（Tracer Bean、跨进程传播）在引入对应 starter 后在此扩展。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
@ConditionalOnProperty(prefix = "cim.obs.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OtelTracingConfig {

    /** 占位：真实 Tracer/Propagator Bean 在链路依赖到位后在此声明。 */
    public OtelTracingConfig(CimObsProperties properties) {
        // 当前仅占位，证明条件装配链路正确；后续扩展不破坏契约
    }
}
