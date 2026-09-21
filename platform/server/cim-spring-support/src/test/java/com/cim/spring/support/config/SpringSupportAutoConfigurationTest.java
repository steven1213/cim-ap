package com.cim.spring.support.config;

import com.cim.core.port.CurrentUserPort;
import com.cim.core.port.EventPublisher;
import com.cim.core.port.TenantPort;
import com.cim.spring.support.i18n.MessageResolver;
import com.cim.spring.support.tenant.TenantFilter;
import com.cim.spring.support.tenant.TenantResolver;
import com.cim.spring.support.trace.TraceIdFilter;
import com.cim.spring.support.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配验收：默认 Bean 就位、多租户按开关启停（对齐 plan.md M0 的 DoD）。
 */
class SpringSupportAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringSupportAutoConfiguration.class));

    @Test
    void registersDefaultBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MessageResolver.class);
            assertThat(context).hasSingleBean(TenantPort.class);
            assertThat(context).hasSingleBean(CurrentUserPort.class);
            assertThat(context).hasSingleBean(EventPublisher.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(TraceIdFilter.class);
        });
    }

    @Test
    void tenantDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(TenantFilter.class);
            assertThat(context).doesNotHaveBean(TenantResolver.class);
        });
    }

    @Test
    void tenantEnabledWhenConfigured() {
        runner.withPropertyValues("cim.tenant.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(TenantFilter.class);
                    assertThat(context).hasSingleBean(TenantResolver.class);
                });
    }
}
