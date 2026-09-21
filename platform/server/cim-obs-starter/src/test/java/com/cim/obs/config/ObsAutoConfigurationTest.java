package com.cim.obs.config;

import com.cim.obs.metrics.MetricsRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ObsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObsAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("cim.obs.enabled=true");

    @Test
    @DisplayName("启用时提供 MetricsRegistry 且指标带 cim. 前缀并落入注册表")
    void providesMetricsRegistry() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MetricsRegistry.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            MetricsRegistry metrics = context.getBean(MetricsRegistry.class);

            metrics.counter("login.total").increment();

            assertThat(registry.find("cim.login.total").counter()).isNotNull();
        });
    }

    @Test
    @DisplayName("关闭时整体降级，不暴露 MetricsRegistry")
    void disabledWhenPropertyOff() {
        runner.withPropertyValues("cim.obs.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(MetricsRegistry.class);
        });
    }

    @Test
    @DisplayName("公共标签被追加到指标")
    void appliesCommonTags() {
        runner.withPropertyValues("cim.obs.metrics.commonTags.app=cim-ap")
                .run(context -> {
                    MeterRegistry registry = context.getBean(MeterRegistry.class);
                    MetricsRegistry metrics = context.getBean(MetricsRegistry.class);
                    // 模拟 actuator 的 MeterRegistryConfigurer：将所有 MeterFilter 套用到注册表
                    registry.config().meterFilter(context.getBean(MeterFilter.class));
                    metrics.counter("order.created", "zone", "east").increment();
                    assertThat(registry.find("cim.order.created").counter())
                            .satisfies(c -> assertThat(c.getId().getTag("app")).isEqualTo("cim-ap"));
                });
    }

    @Configuration
    static class TestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
