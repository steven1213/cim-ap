package com.cim.jpa.config;

import com.cim.jpa.db.DbCapabilities;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Flyway 多目录迁移自动装配（T1.5，见 README §3 / design.md §2.3）。
 *
 * <p><b>配置键全部沿用 Spring Boot</b>（原先的 {@code cim.jpa.flyway.*} 已删除，避免同一概念两套开关）：</p>
 * <ul>
 *   <li>是否跑迁移 → {@code spring.flyway.enabled}（本类条件与其完全一致，含 {@code matchIfMissing=true}）；</li>
 *   <li>迁移目录 → {@code spring.flyway.locations}；<b>用户显式配置时本框架让位</b>，不再改写；</li>
 *   <li>基线 → {@code spring.flyway.baseline-on-migrate}。</li>
 * </ul>
 *
 * <p>只在用户<b>没有</b>显式声明 {@code spring.flyway.locations} 时，本类按当前数据库类型补默认值
 * {@code classpath:db/migration/common} + {@code classpath:db/migration/{vendor}}
 * （vendor ∈ {@code mysql/oracle/postgresql/h2/dm}，其中 {@code dm} 为达梦 DM）。
 * 生产配合 {@code spring.jpa.hibernate.ddl-auto=validate} 使用，表结构由 Flyway 管理。</p>
 *
 * <p>不新建 Flyway Bean，而是通过 {@link FlywayConfigurationCustomizer} 定制 Spring Boot
 * 已装配的 Flyway；当 classpath 无 Flyway 或未开启时整体不加载（优雅降级）。</p>
 */
@AutoConfiguration(after = FlywayAutoConfiguration.class)
@ConditionalOnClass(FluentConfiguration.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CimFlywayAutoConfiguration {

    /** 平台基线迁移目录（classpath 相对路径）。 */
    private static final String BASELINE_ROOT = "db/migration/";

    /**
     * 按数据库类型设定 vendor 迁移目录（仅在用户未显式配置 {@code spring.flyway.locations} 时生效）。
     *
     * <p><b>{@code @Order(HIGHEST_PRECEDENCE)} 是刻意的</b>：本方法会<b>整体改写</b>
     * {@code locations}，必须在任何「追加模块目录」的 customizer 之前执行——否则模块
     * （如 {@code cim-system} 的 {@code classpath:db/migration/system/{vendor}}）加的目录
     * 会被本方法覆盖掉。Spring Boot 按 {@code @Order} 排序后依次应用 customizer。</p>
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnMissingBean(name = "cimFlywayLocationsCustomizer")
    public FlywayConfigurationCustomizer cimFlywayLocationsCustomizer(DataSource dataSource,
                                                                     Environment environment) {
        String explicit = environment.getProperty("spring.flyway.locations");
        if (explicit != null && !explicit.isBlank()) {
            // Spring 键优先：用户自己配了 locations，平台不夺权；模块 customizer 仍会追加自有目录
            return configuration -> { };
        }
        String vendor = resolveVendor(dataSource);
        String[] locations = new String[]{
                "classpath:" + BASELINE_ROOT + "common",
                "classpath:" + BASELINE_ROOT + vendor
        };
        return configuration -> configuration.locations(locations);
    }

    private String resolveVendor(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return DbCapabilities.of(connection.getMetaData().getDatabaseProductName()).product();
        } catch (Exception e) {
            return "common";
        }
    }
}
