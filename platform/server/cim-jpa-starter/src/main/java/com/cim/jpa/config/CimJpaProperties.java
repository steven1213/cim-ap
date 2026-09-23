package com.cim.jpa.config;

import com.cim.jpa.id.IdMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 持久化配置（{@code cim.jpa.*}，见 design.md §5 配置键表）。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cim.jpa")
public class CimJpaProperties {

    /** 主键生成。 */
    private Id id = new Id();

    /** 历史记录。 */
    private History history = new History();

    /** 命名策略。 */
    private Naming naming = new Naming();

    /**
     * 额外数据源（除 Spring Boot 主数据源外，每库独立 EMF/TxManager/Hikari，见 README §3）。
     *
     * <p>key 为逻辑库名（如 {@code second}）；配置存在即激活
     * {@code CimMultiDataSourceAutoConfiguration}，为每个库生成
     * {@code {name}DataSource} / {@code {name}EntityManagerFactory} / {@code {name}TransactionManager}。</p>
     */
    private Map<String, Datasource> datasources = new LinkedHashMap<>();

    /** Flyway 多目录迁移。 */
    private FlywayConfig flyway = new FlywayConfig();

    /** 主键生成配置。 */
    @Getter
    @Setter
    public static class Id {
        /** 生成策略。 */
        private IdMode mode = IdMode.SNOWFLAKE;
        /** 雪花 workerId（应由部署平台下发，见 README §3.1）。 */
        private long workerId = 1L;
        /** 雪花 datacenterId。 */
        private long datacenterId = 1L;
    }

    /** 历史记录配置。 */
    @Getter
    @Setter
    public static class History {
        /** 是否启用自动历史记录。 */
        private boolean enabled = true;
        /** 历史写入模式（关键数据同事务，高频走发件箱异步，见 README §12）。 */
        private HistoryWriteMode writeMode = HistoryWriteMode.IN_TRANSACTION;
    }

    /** 命名策略配置。 */
    @Getter
    @Setter
    public static class Naming {
        /** 物理命名策略：统一小写蛇形（跨库一致，见 README §3）。 */
        private boolean lowercaseSnake = true;
        /** 全局表名前缀（可选）。 */
        private String tablePrefix = "";
    }

    /** 历史写入模式。 */
    public enum HistoryWriteMode {
        /** 与业务同事务写入（强一致，适用关键数据）。 */
        IN_TRANSACTION,
        /** 经事务发件箱异步写入（适用高频数据，失败不影响业务）。 */
        OUTBOX
    }

    /** 单个额外数据源配置（每库独立 EMF/TxManager/Hikari）。 */
    @Getter
    @Setter
    public static class Datasource {
        /** JDBC URL（必填，配置存在即激活本库）。 */
        private String url;
        /** 用户名。 */
        private String username = "sa";
        /** 口令。 */
        private String password = "";
        /** JDBC 驱动类名（可选，Hikari 可由 URL 推断）。 */
        private String driverClassName;
        /** Hibernate 方言（可选，缺省由 Hibernate 自动探测）。 */
        private String dialect;
        /** 本库实体扫描包（逗号分隔；为空则不扫描业务实体）。 */
        private String packagesToScan;
        /** DDL 策略，缺省 {@code none}（生产用 Flyway 管理表结构）。 */
        private String ddlAuto = "none";
        /** 连接池最大连接数。 */
        private int maximumPoolSize = 10;
    }

    /** Flyway 多目录迁移配置。 */
    @Getter
    @Setter
    public static class FlywayConfig {
        /** 是否由 cim 框架接管 Flyway 位置选择（按库类型选 {@code db/migration/{vendor}}）。 */
        private boolean enabled = false;
        /** 覆盖默认位置（逗号分隔）；为空时取 {@code db/migration/common,db/migration/{vendor}}。 */
        private String locations;
        /** 非空 schema 是否 baseline-on-migrate。 */
        private boolean baselineOnMigrate = false;
    }
}
