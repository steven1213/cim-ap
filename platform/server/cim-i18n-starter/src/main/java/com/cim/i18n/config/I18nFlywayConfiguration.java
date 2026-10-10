package com.cim.i18n.config;

import com.cim.jpa.db.DbCapabilities;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 把本模块自带的 i18n 迁移目录接入 Flyway（{@code sys_locale} + {@code sys_i18n}）。
 *
 * <p>与 {@code CimSystemFlywayConfiguration} 同构：读取当前 locations 后<b>追加</b>
 * {@code classpath:db/migration/i18n/{vendor}}。order 取 {@code HIGHEST_PRECEDENCE + 110}，
 * 晚于 cim-system 的 {@code +100}（两者都是追加，顺序不影响结果，仅保持确定性）。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(FluentConfiguration.class)
@ConditionalOnProperty(prefix = "cim.jpa.flyway", name = "enabled", havingValue = "true")
public class I18nFlywayConfiguration {

    private static final String MODULE_ROOT = "db/migration/i18n/";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 110)
    public FlywayConfigurationCustomizer cimI18nFlywayLocations(ObjectProvider<DataSource> dataSourceProvider) {
        return configuration -> {
            String vendor = resolveVendor(dataSourceProvider);
            String moduleLocation = "classpath:" + MODULE_ROOT + vendor;
            if (!exists(moduleLocation)) {
                return;
            }
            List<String> locations = new ArrayList<>();
            for (Location location : configuration.getLocations()) {
                locations.add(location.getDescriptor());
            }
            if (!locations.contains(moduleLocation)) {
                locations.add(moduleLocation);
                configuration.locations(locations.toArray(new String[0]));
            }
        };
    }

    private String resolveVendor(ObjectProvider<DataSource> dataSourceProvider) {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return "common";
        }
        try (Connection connection = dataSource.getConnection()) {
            return DbCapabilities.of(connection.getMetaData().getDatabaseProductName()).product();
        } catch (Exception e) {
            return "common";
        }
    }

    private boolean exists(String location) {
        String path = location.startsWith("classpath:") ? location.substring("classpath:".length()) : location;
        return Thread.currentThread().getContextClassLoader().getResource(path) != null;
    }
}
