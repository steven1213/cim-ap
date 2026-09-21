package com.cim.obs.config;

import com.cim.obs.logging.SensitiveDataMasker;
import com.cim.obs.metrics.MetricsRegistry;
import com.cim.obs.trace.OtelTracingConfig;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.stream.Collectors;

/**
 * cim-obs-starter 自动装配（design.md §2.8 / T4.4）。
 *
 * <p>启用后提供：{@link MetricsRegistry} 门面 + 公共标签 {@link MeterFilter}（指标）；
 * 日志脱敏由 {@link SensitiveDataMasker}/{@code MaskedMessageConverter} 在落盘时生效；
 * 链路 {@link OtelTracingConfig} 条件激活。任一能力可在 {@code cim.obs.*} 下独立关闭。</p>
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

    @Bean
    @ConditionalOnProperty(prefix = "cim.obs.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MeterFilter commonTagsMeterFilter(CimObsProperties props) {
        var tags = props.getMetrics().getCommonTags();
        if (tags == null || tags.isEmpty()) {
            // 无公共标签时返回中性过滤器，避免空集合异常
            return new MeterFilter() {
                @Override
                public MeterFilterReply accept(Meter.Id id) {
                    return MeterFilterReply.NEUTRAL;
                }
            };
        }
        var common = tags.entrySet().stream()
                .map(e -> Tag.of(e.getKey(), String.valueOf(e.getValue())))
                .collect(Collectors.toList());
        return MeterFilter.commonTags(common);
    }
}
