package com.cim.system.autoconfigure;

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
 * 把本模块自带的迁移目录接入 Flyway（design.md §2.10 的「域模块自有 schema」）。
 *
 * <p><b>为什么由模块自己注册</b>：{@code cim-system} 是域模块，它的表属于它自己；
 * 若把 DDL 塞进 {@code cim-bootstrap} 或业务 ap，就变成「模块定义实体、宿主维护表结构」，
 * 加一个模块要改宿主的两处，模块也就不再可整体搬运。</p>
 *
 * <p><b>追加而非改写</b>：本 customizer 读取当前已配置的 locations 再<b>追加</b>
 * {@code classpath:db/migration/system/{vendor}}。因此必须晚于
 * {@code CimFlywayAutoConfiguration.cimFlywayLocationsCustomizer}（后者会整体改写 locations，
 * 它已声明 {@code @Order(HIGHEST_PRECEDENCE)}），故此处用更高的 order 值。</p>
 *
 * <p>仅当 {@code cim.jpa.flyway.enabled=true} 且 Flyway 在 classpath 上时生效；
 * 目录不存在（例如该库型尚无脚本）时静默跳过，避免 Flyway 因无法解析位置而报错。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(FluentConfiguration.class)
@ConditionalOnProperty(prefix = "cim.jpa.flyway", name = "enabled", havingValue = "true")
public class CimSystemFlywayConfiguration {

    /** 模块迁移目录根（classpath 相对路径）。 */
    private static final String MODULE_ROOT = "db/migration/system/";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    public FlywayConfigurationCustomizer cimSystemFlywayLocations(ObjectProvider<DataSource> dataSourceProvider) {
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

    /** 与 {@code CimFlywayAutoConfiguration} 同一套解析逻辑（保证目录名一致）。 */
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

    /** 目录是否存在于 classpath（jar 内亦可解析为 URL）。 */
    private boolean exists(String location) {
        String path = location.startsWith("classpath:") ? location.substring("classpath:".length()) : location;
        return Thread.currentThread().getContextClassLoader().getResource(path) != null;
    }
}
