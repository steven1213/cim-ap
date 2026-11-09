package com.cim.obs.config;

import com.cim.obs.logging.SensitiveDataMasker;
import com.cim.obs.metrics.MetricsRegistry;
import com.cim.obs.trace.OtelTracingConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * cim-obs-starter 自动装配（design.md §2.8 / T4.4）。
 *
 * <p>启用后提供：{@link MetricsRegistry} 门面（统一 {@code cim.} 名前缀）；
 * 日志脱敏由 {@link SensitiveDataMasker}/{@code MaskedMessageConverter} 在落盘时生效；
 * 链路 {@link OtelTracingConfig} 条件激活。</p>
 *
 * <p><b>与 Spring Boot 对齐</b>：公共标签、指标开关、链路开关一律走 {@code management.*}，
 * 本类不注册 {@code MeterFilter}（Boot 的 {@code PropertiesMeterFilter} 已负责
 * {@code management.metrics.tags.*}），避免同一概念两套实现。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cim.obs", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(MeterRegistry.class)
@EnableConfigurationProperties(CimObsProperties.class)
public class ObsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "cim.obs.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MetricsRegistry metricsRegistry(MeterRegistry registry, CimObsProperties props) {
        return new MetricsRegistry(registry, props.getMetrics().getPrefix());
    }
}
