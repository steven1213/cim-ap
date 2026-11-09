package com.cim.jpa.sqllog;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SqlLoggingDataSource} 端到端测试（H2）。
 *
 * <p>核心断言：日志里打出的 SQL **原样再执行一次**能得到同样结果——即真正「可执行」，
 * 而不是只带 {@code ?} 的 Hibernate 输出。</p>
 */
class SqlLoggingDataSourceTest {

    private static final String LOGGER_NAME = "cim.jpa.sql.test";

    private JdbcDataSource h2;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:sqllog_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        h2.setUser("sa");
        h2.setPassword("");

        logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    @DisplayName("日志中的 insert/select 原样可执行（含单引号转义与 NULL）")
    void loggedSqlIsDirectlyExecutable() throws Exception {
        SqlLoggingDataSource dataSource = new SqlLoggingDataSource(h2, LOGGER_NAME, 4000);

        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table t_probe (id int primary key, name varchar(50), note varchar(50))");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into t_probe (id, name, note) values (?, ?, ?)")) {
                insert.setInt(1, 1);
                insert.setString(2, "O'Hara");
                insert.setString(3, null);
                insert.executeUpdate();
            }
            // 清空数据，稍后用「日志里的 SQL」重新灌入
            try (Statement statement = connection.createStatement()) {
                statement.execute("delete from t_probe");
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "select name, note from t_probe where id = ? and name = ?")) {
                select.setInt(1, 1);
                select.setString(2, "O'Hara");
                try (ResultSet resultSet = select.executeQuery()) {
                    assertFalse(resultSet.next(), "刚清空过，应无数据");
                }
            }
        }

        String insertSql = loggedContaining("insert into t_probe");
        assertEquals("insert into t_probe (id, name, note) values (1, 'O''Hara', NULL)", insertSql);

        String selectSql = loggedContaining("select name, note from t_probe");
        assertEquals("select name, note from t_probe where id = 1 and name = 'O''Hara'", selectSql);

        // 1) 日志中的 insert 原样执行 → 数据成功落库
        try (Connection raw = h2.getConnection()) {
            try (Statement statement = raw.createStatement()) {
                statement.executeUpdate(insertSql);
            }
            // 2) 日志中的 select 原样执行 → 查出上面写入的行
            try (Statement statement = raw.createStatement();
                 ResultSet rs = statement.executeQuery(selectSql)) {
                assertTrue(rs.next(), "日志 SQL 应能查到数据");
                assertEquals("O'Hara", rs.getString(1));
                assertNull(rs.getString(2));
            }
        }
    }

    @Test
    @DisplayName("logger 非 DEBUG 时不代理、不输出、功能不受影响")
    void silentWhenLoggerNotDebug() throws Exception {
        logger.setLevel(Level.INFO);
        SqlLoggingDataSource dataSource = new SqlLoggingDataSource(h2, LOGGER_NAME, 4000);
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table t_quiet (id int primary key)");
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "insert into t_quiet (id) values (?)")) {
                insert.setInt(1, 9);
                insert.executeUpdate();
            }
        }
        assertTrue(appender.list.isEmpty(), "INFO 级别不应输出 SQL：" + messages());
    }

    @Test
    @DisplayName("批量 addBatch/executeBatch 逐条输出可执行 SQL")
    void logsEachBatchEntry() throws Exception {
        SqlLoggingDataSource dataSource = new SqlLoggingDataSource(h2, LOGGER_NAME, 4000);
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table t_batch (id int primary key, tag varchar(20))");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into t_batch (id, tag) values (?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "a");
                ps.addBatch();
                ps.setInt(1, 2);
                ps.setString(2, "b");
                ps.addBatch();
                assertEquals(2, ps.executeBatch().length);
            }
        }
        List<String> lines = messages();
        String first = lines.stream().filter(l -> l.contains("values (1, 'a')")).findFirst().orElseThrow();
        String second = lines.stream().filter(l -> l.contains("values (2, 'b')")).findFirst().orElseThrow();
        assertTrue(first.endsWith("insert into t_batch (id, tag) values (1, 'a')"), first);
        assertTrue(second.endsWith("insert into t_batch (id, tag) values (2, 'b')"), second);
    }

    @Test
    @DisplayName("无参 Statement / createStatement 同样输出可执行 SQL")
    void logsPlainStatement() throws Exception {
        SqlLoggingDataSource dataSource = new SqlLoggingDataSource(h2, LOGGER_NAME, 4000);
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("create table t_plain (id int primary key)");
                statement.execute("insert into t_plain (id) values (5)");
                try (ResultSet rs = statement.executeQuery("select id from t_plain where id = 5")) {
                    assertTrue(rs.next());
                }
            }
        }
        List<String> lines = messages();
        String select = lines.stream()
                .filter(l -> l.contains("select id from t_plain"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未捕获无参 Statement 日志：" + lines));
        assertTrue(select.endsWith("select id from t_plain where id = 5"), select);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** 取出日志行中「耗时前缀之后」的 SQL 原文。 */
    private String loggedContaining(String fragment) {
        String line = messages().stream()
                .filter(m -> m.contains(fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未捕获到包含 [" + fragment + "] 的 SQL 日志：" + messages()));
        int index = line.indexOf("| ");
        assertTrue(index > 0, "日志缺少耗时前缀：" + line);
        return line.substring(index + 2);
    }
}
