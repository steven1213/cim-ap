package com.cim.obs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * cim-obs-starter 配置键（前缀 {@code cim.obs.*}，见 design.md §2.8 / T4.4）。
 *
 * <p>指标/日志脱敏/链路三类能力均可通过配置开关独立启停；缺席时优雅降级。</p>
 */
@ConfigurationProperties(prefix = "cim.obs")
public class CimObsProperties {

    /** 总开关。 */
    private boolean enabled = true;

    /** 指标（Prometheus）。 */
    private MetricsProperties metrics = new MetricsProperties();

    /** 日志脱敏。 */
    private LoggingProperties logging = new LoggingProperties();

    /** 链路（OTel，条件激活）。 */
    private TraceProperties trace = new TraceProperties();

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

    public TraceProperties getTrace() {
        return trace;
    }

    public void setTrace(TraceProperties trace) {
        this.trace = trace;
    }

    /** 指标配置。 */
    public static class MetricsProperties {
        /** 是否启用指标收集。 */
        private boolean enabled = true;
        /** 指标名前缀（默认 {@code cim.}）。 */
        private String prefix = "cim.";
        /** 公共标签（如 app/env），追加到所有指标。 */
        private Map<String, String> commonTags = new LinkedHashMap<>();

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

        public Map<String, String> getCommonTags() {
            return commonTags;
        }

        public void setCommonTags(Map<String, String> commonTags) {
            this.commonTags = commonTags;
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

    /** 链路配置。 */
    public static class TraceProperties {
        /** 是否启用 OTel 链路桥接（需 micrometer-tracing + otel bridge 在 classpath）。 */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
