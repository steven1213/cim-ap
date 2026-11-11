package com.cim.spring.support.config;

import com.cim.spring.support.dispatch.CimDispatchController;
import com.cim.spring.support.dispatch.CimDispatchProperties;
import com.cim.spring.support.dispatch.CimMessageDispatcher;
import com.cim.spring.support.dispatch.CimMessageRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 消息分发模式的自动装配（默认关闭，见 {@code api-convention.md §7}）。
 *
 * <p>{@code enabled=false} 时**不装配任何 Bean**——包括注册表与控制器，因此没有扫描开销，
 * 也不会在应用上凭空多出一个通配端点。</p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cim.api.dispatch", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CimDispatchProperties.class)
public class CimDispatchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CimMessageRegistry cimMessageRegistry(ListableBeanFactory beanFactory,
                                                 CimDispatchProperties properties) {
        return new CimMessageRegistry(beanFactory, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CimMessageDispatcher cimMessageDispatcher(CimMessageRegistry registry,
                                                     ListableBeanFactory beanFactory,
                                                     ObjectProvider<ObjectMapper> objectMapper) {
        return new CimMessageDispatcher(registry, beanFactory,
                objectMapper.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()));
    }

    @Bean
    @ConditionalOnMissingBean
    public CimDispatchController cimDispatchController(CimMessageDispatcher dispatcher) {
        return new CimDispatchController(dispatcher);
    }
}
