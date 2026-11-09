package com.cim.obs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cim-obs-starter 配置键（前缀 {@code cim.obs.*}，见 design.md §2.8 / T4.4）。
 *
 * <p>指标/日志脱敏两类能力可通过配置开关独立启停；缺席时优雅降级。</p>
 *
 * <p><b>配置边界（与 Spring Boot 对齐）——本类刻意只留 Spring 没有的概念：</b></p>
 * <ul>
 *   <li>指标公共标签 → {@code management.metrics.tags.*}（Boot 的 {@code PropertiesMeterFilter} 统一套用，
 *       本 starter 不再自建 {@code MeterFilter}，避免两套标签互相覆盖）；</li>
 *   <li>链路开关 → {@code management.tracing.enabled}；</li>
 *   <li>导出端点/采样率 → {@code management.*} 既有键。</li>
 * </ul>
 * <p>故原先的 {@code cim.obs.metrics.common-tags} 与 {@code cim.obs.trace.enabled} 已删除。</p>
 */
@ConfigurationProperties(prefix = "cim.obs")
public class CimObsProperties {

    /** 总开关。 */
    private boolean enabled = true;

    /** 指标（Prometheus）。 */
    private MetricsProperties metrics = new MetricsProperties();

    /** 日志脱敏。 */
    private LoggingProperties logging = new LoggingProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics;
    }

    public LoggingProperties getLogging() {
        return logging;
    }

    public void setLogging(LoggingProperties logging) {
        this.logging = logging;
    }

    /** 指标配置。 */
    public static class MetricsProperties {
        /** 是否启用指标收集。 */
        private boolean enabled = true;
        /** 指标名前缀（默认 {@code cim.}；Spring/Micrometer 无「全局名前缀」这一概念，故保留）。 */
        private String prefix = "cim.";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }

    /** 日志脱敏配置。 */
    public static class LoggingProperties {
        /** 是否对日志报文脱敏。 */
        private boolean maskingEnabled = true;

        public boolean isMaskingEnabled() {
            return maskingEnabled;
        }

        public void setMaskingEnabled(boolean maskingEnabled) {
            this.maskingEnabled = maskingEnabled;
        }
    }
}
