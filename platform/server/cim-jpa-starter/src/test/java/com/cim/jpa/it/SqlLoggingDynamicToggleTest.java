package com.cim.jpa.it;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cim.jpa.sqllog.SqlLogAdminController;
import com.cim.jpa.sqllog.SqlLoggingDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行时动态开关验收：进程启动时 logger 为 INFO（无 SQL 输出），
 * 通过 {@code POST /sys/sql-log}（此处直接调用端点 Bean）切到 DEBUG 后**立即**开始输出，
 * 切回 INFO 后立即静默——全程不重启、不改配置。
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:sqllog_toggle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "cim.jpa.sql-log.admin-enabled=true",
        "logging.level.cim.jpa.sql=INFO"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SqlLoggingDynamicToggleTest {

    private static final String SQL_LOGGER = "cim.jpa.sql";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EquipmentDefRepository repository;

    @Autowired
    private SqlLogAdminController adminController;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(SQL_LOGGER);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAndRestore() {
        logger.detachAppender(appender);
        LoggingSystem.get(getClass().getClassLoader()).setLogLevel(SQL_LOGGER, LogLevel.INFO);
    }

    @Test
    @DisplayName("HTTP 端点逐次切换：INFO 静默 → DEBUG 输出可执行 SQL → INFO 再静默")
    void togglesAtRuntime() {
        String id = save("EQ-DYN");

        // 1) 启动级别 INFO：查询/写入都不输出
        appender.list.clear();
        assertThat(repository.findById(id)).isPresent();
        assertThat(sqlMessages()).isEmpty();

        // 2) 打开：一次请求即可，无需重启
        Map<String, Object> on = adminController.change(null, Boolean.TRUE, null).data();
        assertThat(on).containsEntry("level", "DEBUG").containsEntry("active", true);
        assertThat(asStringMap(on.get("dataSources"))).containsEntry("dataSource", "WRAPPED");
        assertThat(((SqlLoggingDataSource) dataSource).isLoggingActive()).isTrue();

        appender.list.clear();
        assertThat(repository.findById(id)).isPresent();
        assertThat(sqlMessages())
                .anyMatch(line -> line.startsWith("[SQL] ")
                        && line.contains("from equipment_def")
                        && line.contains("'" + id + "'"));

        // 3) 关闭：立即静默
        Map<String, Object> off = adminController.change(null, Boolean.FALSE, null).data();
        assertThat(off).containsEntry("level", "INFO").containsEntry("active", false);

        appender.list.clear();
        assertThat(repository.findById(id)).isPresent();
        assertThat(sqlMessages()).isEmpty();
    }

    @Test
    @DisplayName("支持按 level 精确设定（如 TRACE）")
    void setsExplicitLevel() {
        Map<String, Object> trace = adminController.change(null, null, "TRACE").data();
        assertThat(trace).containsEntry("level", "TRACE").containsEntry("active", true);

        appender.list.clear();
        save("EQ-TRACE");
        assertThat(sqlMessages()).anyMatch(line -> line.contains("insert into equipment_def"));
    }

    private String save(String code) {
        EquipmentDef def = new EquipmentDef();
        def.setCode(code);
        def.setName("光刻机");
        def.setCapacity(1);
        repository.saveAndFlush(def);
        return def.getId();
    }

    private List<String> sqlMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> asStringMap(Object value) {
        return (Map<String, String>) value;
    }
}
