package com.cim.jpa.config;

import com.cim.core.event.EntityLifecycleCallback;
import com.cim.core.port.CurrentUserPort;
import com.cim.core.port.IdGenerator;
import com.cim.core.port.TenantPort;
import com.cim.jpa.db.DbCapability;
import com.cim.jpa.id.SnowflakeIdGenerator;
import com.cim.jpa.id.UuidV7IdGenerator;
import com.cim.spring.support.security.AnonymousCurrentUserPort;
import com.cim.spring.support.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 持久化自动装配验收：默认雪花主键、按配置切换 UUIDv7、回调与能力抽象就位。
 *
 * <p>本测试自包含：仅加载 {@link JpaAutoConfiguration}，并手工提供 {@link CurrentUserPort} /
 * {@link TenantPort}（避免引入 Web 相关的自动装配）。</p>
 */
class JpaAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JpaAutoConfiguration.class))
            .withBean(CurrentUserPort.class, AnonymousCurrentUserPort::new)
            .withBean(TenantPort.class, TenantContext::new);

    @Test
    void defaultIdGeneratorIsSnowflake() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(IdGenerator.class);
            assertThat(context.getBean(IdGenerator.class)).isInstanceOf(SnowflakeIdGenerator.class);
            assertThat(context).hasSingleBean(DbCapability.class);
            assertThat(context).hasSingleBean(EntityLifecycleCallback.class);
        });
    }

    @Test
    void uuidV7ModeSwitchesGenerator() {
        runner.withPropertyValues("cim.jpa.id.mode=UUIDV7")
                .run(context -> assertThat(context.getBean(IdGenerator.class))
                        .isInstanceOf(UuidV7IdGenerator.class));
    }
}
