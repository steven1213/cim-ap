package com.cim.jpa.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 主键机器号/机房号的环境变量注入验收。
 *
 * <p>对应各 ap 的 yml 写法 {@code worker-id: ${CIM_WORKER_ID:1}}：环境变量优先，
 * 未提供时回落默认值 1（单机 / 开发场景）。容器化部署必须注入，否则多副本会撞号。</p>
 */
class IdEnvInjectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(PropsConfig.class));

    @Test
    @DisplayName("CIM_WORKER_ID / CIM_DATACENTER_ID 覆盖 yml 默认值")
    void envOverridesDefault() {
        runner.withPropertyValues(
                        "CIM_WORKER_ID=7",
                        "CIM_DATACENTER_ID=3",
                        "cim.jpa.id.worker-id=${CIM_WORKER_ID:1}",
                        "cim.jpa.id.datacenter-id=${CIM_DATACENTER_ID:1}")
                .run(context -> {
                    CimJpaProperties props = context.getBean(CimJpaProperties.class);
                    assertThat(props.getId().getWorkerId()).isEqualTo(7L);
                    assertThat(props.getId().getDatacenterId()).isEqualTo(3L);
                });
    }

    @Test
    @DisplayName("未提供环境变量时回落为 1")
    void fallsBackToDefault() {
        runner.withPropertyValues(
                        "cim.jpa.id.worker-id=${CIM_WORKER_ID:1}",
                        "cim.jpa.id.datacenter-id=${CIM_DATACENTER_ID:1}")
                .run(context -> {
                    CimJpaProperties props = context.getBean(CimJpaProperties.class);
                    assertThat(props.getId().getWorkerId()).isEqualTo(1L);
                    assertThat(props.getId().getDatacenterId()).isEqualTo(1L);
                });
    }

    /** 仅绑定属性类，不拉起 JPA。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CimJpaProperties.class)
    static class PropsConfig {
    }
}
