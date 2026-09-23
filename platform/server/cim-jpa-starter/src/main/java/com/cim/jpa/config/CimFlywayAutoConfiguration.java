package com.cim.jpa.config;

import com.cim.jpa.db.DbCapabilities;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Flyway 多目录迁移自动装配（T1.5，见 README §3 / design.md §2.3）。
 *
 * <p>当 {@code cim.jpa.flyway.enabled=true} 时激活：按当前数据库类型选择迁移位置
 * {@code classpath:db/migration/common} + {@code classpath:db/migration/{vendor}}
 * （vendor ∈ {@code mysql/oracle/postgresql/h2}）。生产配合
 * {@code spring.jpa.hibernate.ddl-auto=validate} 使用，表结构由 Flyway 管理。</p>
 *
 * <p>不新建 Flyway Bean，而是通过 {@link FlywayConfigurationCustomizer} 定制 Spring Boot
 * 已装配的 Flyway；当 classpath 无 Flyway 或未显式开启时整体不加载（优雅降级）。</p>
 */
@AutoConfiguration(after = FlywayAutoConfiguration.class)
@ConditionalOnClass(FluentConfiguration.class)
@ConditionalOnProperty(prefix = "cim.jpa.flyway", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CimJpaProperties.class)
public class CimFlywayAutoConfiguration {

    /** 按数据库类型追加 vendor 迁移目录。 */
    @Bean
    @ConditionalOnMissingBean(name = "cimFlywayLocationsCustomizer")
    public FlywayConfigurationCustomizer cimFlywayLocationsCustomizer(DataSource dataSource,
                                                                     CimJpaProperties properties) {
        String vendor = resolveVendor(dataSource);
        String[] locations;
        String override = properties.getFlyway().getLocations();
        if (override != null && !override.isBlank()) {
            locations = override.split("\\s*,\\s*");
        } else {
            locations = new String[]{
                    "classpath:db/migration/common",
                    "classpath:db/migration/" + vendor
            };
        }
        boolean baseline = properties.getFlyway().isBaselineOnMigrate();
        return configuration -> configuration
                .locations(locations)
                .baselineOnMigrate(baseline);
    }

    private String resolveVendor(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return DbCapabilities.of(connection.getMetaData().getDatabaseProductName()).product();
        } catch (Exception e) {
            return "common";
        }
    }
}
