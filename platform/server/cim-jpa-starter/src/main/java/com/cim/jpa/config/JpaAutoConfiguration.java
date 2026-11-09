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
import com.cim.jpa.sqllog.SqlLogAdminController;
import com.cim.jpa.sqllog.SqlLoggingDataSourcePostProcessor;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

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

    /**
     * 默认物理命名策略：小写蛇形（跨库一致），可选全局表名前缀。
     *
     * <p>与 Spring Boot 对齐：小写蛇形本就是 Boot 默认的
     * {@code CamelCaseToUnderscoresNamingStrategy}，故不再提供 {@code cim.jpa.naming.lowercase-snake}
     * 开关。用户若显式声明了标准键——{@code spring.jpa.hibernate.naming.physical-strategy} 或
     * {@code spring.jpa.properties.hibernate.physical_naming_strategy}——本框架<b>让位</b>，不覆盖。</p>
     */
    @Bean
    public HibernatePropertiesCustomizer cimNamingCustomizer(CimJpaProperties properties,
                                                             Environment environment) {
        String explicit = environment.getProperty("spring.jpa.hibernate.naming.physical-strategy");
        if (explicit == null || explicit.isBlank()) {
            explicit = environment.getProperty("spring.jpa.properties.hibernate.physical_naming_strategy");
        }
        if (explicit != null && !explicit.isBlank()) {
            log.info("[cim-jpa] 检测到显式物理命名策略（{}），cim 默认策略让位", explicit);
            return hibernateProperties -> { };
        }
        String tablePrefix = properties.getNaming().getTablePrefix();
        return hibernateProperties -> hibernateProperties.put("hibernate.physical_naming_strategy",
                new LowercaseSnakeNamingStrategy(tablePrefix));
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

    /**
     * SQL 调试日志：把（主 + 多库）全部 {@code DataSource} 包装为可打印「可执行 SQL」的数据源
     * （见 {@link com.cim.jpa.sqllog.SqlLoggingDataSource}）。
     *
     * <p>是否安装代理由 {@code cim.jpa.sql-log.enabled}（默认 {@code true}）决定；**是否输出**
     * 由 logger 级别在运行期实时决定，故可经 {@code POST /sys/sql-log} 或 actuator
     * {@code /actuator/loggers} 动态开关，无需重启。</p>
     *
     * <p>必须声明为 {@code static}：BeanPostProcessor 需在其他普通 Bean 之前实例化，
     * 静态工厂方法可避免过早初始化本配置类。</p>
     */
    @Bean
    static BeanPostProcessor cimSqlLogDataSourcePostProcessor(Environment environment) {
        CimJpaProperties.SqlLog config = sqlLogConfig(environment);
        return new SqlLoggingDataSourcePostProcessor(config.isEnabled(), config.getLoggerName(),
                config.getMaxSqlLength());
    }

    /**
     * 运行时开关端点 {@code GET/POST /sys/sql-log}（{@code cim.jpa.sql-log.admin-enabled=true} 才装配）。
     *
     * <p>⚠️ 会改变进程日志级别并使参数值进入日志，仅调试环境开启；生产须保持关闭或由宿主
     * 安全链收紧该路径。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "cim.jpa.sql-log", name = "admin-enabled", havingValue = "true")
    @ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
    public SqlLogAdminController sqlLogAdminController(Environment environment,
                                                       ApplicationContext applicationContext) {
        CimJpaProperties.SqlLog config = sqlLogConfig(environment);
        log.warn("[cim-jpa] SQL 日志动态开关已暴露：{}（平台默认链对 /sys/** 为 permitAll，"
                        + "生产务必关闭 cim.jpa.sql-log.admin-enabled 或由安全链收紧该路径）",
                SqlLogAdminController.PATH);
        return new SqlLogAdminController(config.getLoggerName(), config.getMaxSqlLength(), applicationContext);
    }

    private static CimJpaProperties.SqlLog sqlLogConfig(Environment environment) {
        return Binder.get(environment)
                .bind("cim.jpa.sql-log", CimJpaProperties.SqlLog.class)
                .orElseGet(CimJpaProperties.SqlLog::new);
    }
}
