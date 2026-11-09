package com.cim.jpa.it;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cim.jpa.sqllog.SqlLoggingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code cim.jpa.sql-log.enabled=true} 场景的自动装配验收（H2）：
 * 数据源被代理、连接池可解包（健康检查/指标不受影响）、Hibernate 发出的 SQL
 * 以「参数已内联」的形式输出，且该 SQL 原样可执行。
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:sqllog_explicit;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "cim.jpa.sql-log.enabled=true",
        "logging.level.cim.jpa.sql=DEBUG"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SqlLoggingAutoConfigurationTest {

    private static final String SQL_LOGGER = "cim.jpa.sql";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EquipmentDefRepository repository;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(SQL_LOGGER);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    @DisplayName("数据源被代理，底层连接池仍可解包（Boot 健康检查/指标兼容）")
    void wrapsDataSourceButKeepsPoolReachable() {
        assertThat(dataSource).isInstanceOf(SqlLoggingDataSource.class);
        assertThat(dataSource).isInstanceOf(DelegatingDataSource.class);
        DataSource target = ((DelegatingDataSource) dataSource).getTargetDataSource();
        assertThat(target).isInstanceOf(HikariDataSource.class);
    }

    @Test
    @DisplayName("Hibernate 查询日志为可执行 SQL（无占位符）且能原样执行")
    void hibernateSqlIsLoggedExecutable() throws Exception {
        EquipmentDef def = new EquipmentDef();
        def.setCode("EQ-SQLLOG");
        def.setName("光刻机");
        def.setCapacity(2);
        repository.saveAndFlush(def);
        String id = def.getId();
        assertThat(id).isNotBlank();

        appender.list.clear();
        assertThat(repository.findById(id)).isPresent();

        String line = messages().stream()
                .filter(m -> m.contains("from equipment_def"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未捕获到 Hibernate SQL 日志：" + messages()));
        String sql = line.substring(line.indexOf("| ") + 2);

        assertThat(line).startsWith("[SQL] ");
        assertThat(sql).doesNotContain("= ?").doesNotContain("=?");
        assertThat(sql).contains("'" + id + "'");

        // 日志中的 SQL 原样执行 → 能取到同一行（证明「可执行」而非仅格式化）
        try (Connection raw = ((DelegatingDataSource) dataSource).getTargetDataSource().getConnection();
             Statement statement = raw.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
        }
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
