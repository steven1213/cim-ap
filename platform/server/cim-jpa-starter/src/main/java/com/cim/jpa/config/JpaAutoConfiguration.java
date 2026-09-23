package com.cim.jpa.config;

import com.cim.core.event.EntityLifecycleCallback;
import com.cim.core.port.CurrentUserPort;
import com.cim.core.port.IdGenerator;
import com.cim.core.port.TenantPort;
import com.cim.jpa.audit.LifecycleCallbackRegistrar;
import com.cim.jpa.audit.SpringEntityLifecycleCallback;
import com.cim.jpa.db.DbCapability;
import com.cim.jpa.db.DbCapabilities;
import com.cim.jpa.history.HistoryRecorder;
import com.cim.jpa.id.IdMode;
import com.cim.jpa.id.SnowflakeIdGenerator;
import com.cim.jpa.id.UuidV7IdGenerator;
import com.cim.jpa.tenant.TenantFilterApplier;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 持久化自动装配（见 design.md §2.3）。
 *
 * <p>提供应用层主键生成、物理命名策略、数据库能力抽象，并把生命周期回调注册进
 * {@code cim-core}；全部以 {@code @ConditionalOnMissingBean} 允许覆盖（见 README §8）。</p>
 */
@Slf4j
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean")
@EnableConfigurationProperties(CimJpaProperties.class)
public class JpaAutoConfiguration {

    /** 历史写入器：写操作后按 {@code @History} 策略落该实体专属历史表（见 README §9）。 */
    @Bean
    @ConditionalOnMissingBean
    public HistoryRecorder historyRecorder(ObjectProvider<EntityManager> entityManagerProvider,
                                           CimJpaProperties properties) {
        return new HistoryRecorder(entityManagerProvider, properties);
    }

    /** 应用层主键生成器（雪花 / UUIDv7，见 README §3）。 */
    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator idGenerator(CimJpaProperties properties) {
        CimJpaProperties.Id id = properties.getId();
        if (id.getMode() == IdMode.UUIDV7) {
            log.info("[cim-jpa] id mode = UUIDV7");
            return new UuidV7IdGenerator();
        }
        log.info("[cim-jpa] id mode = SNOWFLAKE (workerId={}, datacenterId={})",
                id.getWorkerId(), id.getDatacenterId());
        return new SnowflakeIdGenerator(id.getWorkerId(), id.getDatacenterId());
    }

    /** 生命周期回调：注入主键 + 填充审计/租户。 */
    @Bean
    @ConditionalOnMissingBean
    public EntityLifecycleCallback entityLifecycleCallback(IdGenerator idGenerator,
                                                           CurrentUserPort currentUserPort,
                                                           TenantPort tenantPort) {
        return new SpringEntityLifecycleCallback(idGenerator, currentUserPort, tenantPort);
    }

    /** 将回调注册进 cim-core 静态持有者。 */
    @Bean
    public LifecycleCallbackRegistrar lifecycleCallbackRegistrar(EntityLifecycleCallback callback) {
        return new LifecycleCallbackRegistrar(callback);
    }

    /** 统一小写蛇形命名策略（跨库一致）。 */
    @Bean
    public HibernatePropertiesCustomizer cimNamingCustomizer(CimJpaProperties properties) {
        return hibernateProperties -> {
            if (properties.getNaming().isLowercaseSnake()) {
                hibernateProperties.put("hibernate.physical_naming_strategy",
                        new LowercaseSnakeNamingStrategy(properties.getNaming().getTablePrefix()));
            }
        };
    }

    /** 数据库能力抽象（从 DataSource 元数据解析；多数据源时取唯一/主源，歧义则跳过）。 */
    @Bean
    @ConditionalOnMissingBean
    public DbCapability dbCapability(ObjectProvider<DataSource> dataSourceProvider) {
        DataSource dataSource = dataSourceProvider.getIfUnique();
        if (dataSource == null) {
            return DbCapabilities.of(null);
        }
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            log.info("[cim-jpa] detected database product: {}", product);
            return DbCapabilities.of(product);
        } catch (Exception e) {
            log.warn("[cim-jpa] resolve DbCapability failed: {}", e.getMessage());
            return DbCapabilities.of(null);
        }
    }

    /** 租户过滤器启用器（按 {@code TenantContext} 自动启用 Hibernate 租户隔离）。 */
    @Bean
    @ConditionalOnMissingBean
    public TenantFilterApplier tenantFilterApplier() {
        return new TenantFilterApplier();
    }
}
