package com.cim.jpa.config;

import com.cim.jpa.id.IdMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 持久化配置（{@code cim.jpa.*}，见 design.md §5 配置键表）。
 *
 * <p><b>配置边界（与 Spring Boot 对齐）</b>：凡 Spring Boot 已有标准键的概念，一律用 Spring 的键，
 * 本类<b>不</b>另立门户——</p>
 * <ul>
 *   <li>迁移开关/目录/baseline → {@code spring.flyway.*}（原 {@code cim.jpa.flyway.*} 已删除）；</li>
 *   <li>物理命名策略 → {@code spring.jpa.hibernate.naming.physical-strategy} 或
 *       {@code spring.jpa.properties.hibernate.physical_naming_strategy}（显式配置时本框架让位）；</li>
 *   <li>主数据源 → {@code spring.datasource.*}；</li>
 *   <li>SQL 打印级别 → {@code logging.level.*}（见 {@link SqlLog}）。</li>
 * </ul>
 * <p>本类只保留 Spring 没有对应概念的项：雪花主键、历史表、表名前缀、额外数据源、可执行 SQL 日志。</p>
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

    /** SQL 调试日志（把占位符替换为实参，输出可直接执行的单行 SQL）。 */
    private SqlLog sqlLog = new SqlLog();

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
        /**
         * 全局表名前缀（可选，Spring/Hibernate 无对应键，故保留在 {@code cim.jpa} 下）。
         *
         * <p>小写蛇形本身是<b>框架默认</b>（等价 Spring Boot 默认的
         * {@code CamelCaseToUnderscoresNamingStrategy}），不再提供开关；如需换策略请用
         * {@code spring.jpa.hibernate.naming.physical-strategy}，框架检测到显式配置即让位。</p>
         */
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

    /**
     * SQL 调试日志配置（{@code cim.jpa.sql-log.*}）。
     *
     * <p>启用后所有 {@code DataSource} 被代理，JPA/MyBatis/JdbcTemplate 发出的 SQL 都会以
     * {@code [SQL] 3.4ms | select ... where id=1 and name='x'} 的**可直接执行**形式打到
     * {@link #loggerName}。**是否输出由该 logger 的级别实时决定**（DEBUG/TRACE 才打），
     * 因此可运行时切换，无需重启：
     * {@code POST /sys/sql-log?enabled=true}（需 {@link #adminEnabled}）
     * 或 actuator {@code POST /actuator/loggers/cim.jpa.sql}。仅用于排障，勿在生产开启。</p>
     */
    @Getter
    @Setter
    public static class SqlLog {
        /**
         * 是否安装数据源代理（默认 {@code true}）。代理是「可动态开关」的前提；
         * 设为 {@code false} 则完全不包装（零开销，但也无法运行时打开）。
         * 关闭状态下每次取连接只多一次日志级别判断，语句层不建代理。
         */
        private boolean enabled = true;
        /** 输出所用 logger 名（运行时切成 DEBUG/TRACE 即输出，切回 INFO 即静默）。 */
        private String loggerName = "cim.jpa.sql";
        /** 单条 SQL 最大输出长度，超出截断（默认 4000）。 */
        private int maxSqlLength = 4000;
        /**
         * 是否暴露动态开关端点 {@code GET/POST /sys/sql-log}（默认 {@code false}）。
         *
         * <p>⚠️ 该端点会改变进程日志级别、导致参数值进入日志，**仅在调试环境开启**，
         * 生产须由宿主安全链收紧或保持关闭。</p>
         */
        private boolean adminEnabled = false;
    }
}
