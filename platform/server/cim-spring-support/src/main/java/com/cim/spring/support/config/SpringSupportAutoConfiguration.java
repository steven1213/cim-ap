package com.cim.spring.support.config;

import com.cim.core.port.CurrentUserPort;
import com.cim.core.port.EventPublisher;
import com.cim.core.port.TenantPort;
import com.cim.spring.support.event.SpringEventPublisher;
import com.cim.spring.support.i18n.DefaultMessageResolver;
import com.cim.spring.support.i18n.MessageResolver;
import com.cim.spring.support.security.AnonymousCurrentUserPort;
import com.cim.spring.support.tenant.HeaderTenantResolver;
import com.cim.spring.support.tenant.TenantContext;
import com.cim.spring.support.tenant.TenantFilter;
import com.cim.spring.support.tenant.TenantProperties;
import com.cim.spring.support.tenant.TenantResolver;
import com.cim.spring.support.trace.TraceIdFilter;
import com.cim.spring.support.web.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * cim-spring-support 自动装配（见 design.md §5）。
 *
 * <p>提供统一响应/异常、链路、租户上下文的默认实现；均以
 * {@code @ConditionalOnMissingBean} 允许业务覆盖（可插拔，见 README §8）。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(TenantProperties.class)
public class SpringSupportAutoConfiguration {

    /** 全局异常处理器（依赖 {@link MessageResolver}）。 */
    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(MessageResolver messageResolver) {
        return new GlobalExceptionHandler(messageResolver);
    }

    /** 消息解析默认实现（被 {@code cim-i18n-starter} 的数据库实现覆盖）。 */
    @Bean
    @ConditionalOnMissingBean(MessageResolver.class)
    public MessageResolver messageResolver() {
        return new DefaultMessageResolver();
    }

    /** 租户端口：暴露 {@link TenantContext}（其内部为 ThreadLocal 静态上下文）。 */
    @Bean
    @ConditionalOnMissingBean(TenantPort.class)
    public TenantPort tenantPort() {
        return new TenantContext();
    }

    /** 当前操作人端口默认实现（匿名）；被 {@code cim-auth-starter} 覆盖。 */
    @Bean
    @ConditionalOnMissingBean(CurrentUserPort.class)
    public CurrentUserPort currentUserPort() {
        return new AnonymousCurrentUserPort();
    }

    /** 领域事件发布默认实现；被 {@code cim-mq-starter} 增强实现覆盖。 */
    @Bean
    @ConditionalOnMissingBean(EventPublisher.class)
    public EventPublisher eventPublisher(org.springframework.context.ApplicationEventPublisher publisher) {
        return new SpringEventPublisher(publisher);
    }

    /** 链路过滤器。 */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    /** 默认租户解析策略（请求头）。 */
    @Bean
    @ConditionalOnMissingBean(TenantResolver.class)
    @ConditionalOnProperty(prefix = "cim.tenant", name = "enabled", havingValue = "true")
    public TenantResolver tenantResolver(TenantProperties properties) {
        return new HeaderTenantResolver(properties);
    }

    /** 租户过滤器（仅在启用多租户时装配）。 */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "cim.tenant", name = "enabled", havingValue = "true")
    public TenantFilter tenantFilter(TenantResolver tenantResolver) {
        return new TenantFilter(tenantResolver);
    }
}
