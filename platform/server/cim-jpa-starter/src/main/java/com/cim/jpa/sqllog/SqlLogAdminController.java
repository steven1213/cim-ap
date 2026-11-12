package com.cim.jpa.sqllog;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.Result;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 可执行 SQL 日志的**运行时开关**（{@code GET/POST /sys/sql-log}）。
 *
 * <p>改的是 logger 级别（经 Spring Boot {@link LoggingSystem}，对 logback/log4j2 通用），
 * 因此立即生效、无需重启；等效于 actuator 的 {@code POST /actuator/loggers/cim.jpa.sql}，
 * 但一次请求即可完成「开启/关闭 + 回读状态」。</p>
 *
 * <p>⚠️ 由 {@code cim.jpa.sql-log.admin-enabled=true} 才装配（默认不装配）。
 * 该操作会改变进程日志级别并使参数值进入日志，生产须保持关闭或由宿主安全链收紧
 * {@code /sys/sql-log}。</p>
 *
 * <p><b>类上的 {@code @ConditionalOnProperty} 不是重复，是必需品</b>：本类是
 * {@code @RestController}，也就是 {@code @Component}。宿主若用
 * {@code scanBasePackages="com.cim"}（{@code cim-bootstrap} 就是），组件扫描会**绕过**自动装配的
 * 开关直接注册它——而它的构造器要 {@code String loggerName}，没人能注入，
 * 启动即 {@code No qualifying bean of type 'java.lang.String'}（已实测）。那个报错完全不提
 * 「你只是没开 sql-log 开关」，排查成本极高。把同一个条件写到类上，扫描阶段就会跳过它。</p>
 */
@Slf4j
@RestController
@ConditionalOnProperty(prefix = "cim.jpa.sql-log", name = "admin-enabled", havingValue = "true")
public class SqlLogAdminController {

    /** 端点路径（宿主可用它配置安全链）。 */
    public static final String PATH = "/sys/sql-log";

    private final String loggerName;
    private final int maxSqlLength;
    private final ApplicationContext applicationContext;

    public SqlLogAdminController(String loggerName, int maxSqlLength, ApplicationContext applicationContext) {
        this.loggerName = loggerName;
        this.maxSqlLength = maxSqlLength;
        this.applicationContext = applicationContext;
    }

    /** 当前状态：级别、是否在输出、各 DataSource 是否已装代理。 */
    @GetMapping(PATH)
    public Result<Map<String, Object>> status() {
        return Result.ok(snapshot());
    }

    /**
     * 开关或指定级别。
     *
     * <pre>
     * POST /sys/sql-log?enabled=true            → 切 DEBUG（开始输出）
     * POST /sys/sql-log?enabled=false           → 切 INFO（静默）
     * POST /sys/sql-log?level=TRACE             → 指定级别
     * POST /sys/sql-log  {"enabled": true}      → JSON 等价写法
     * </pre>
     */
    @PostMapping(PATH)
    public Result<Map<String, Object>> change(@RequestBody(required = false) Map<String, Object> body,
                                              @RequestParam(required = false) Boolean enabled,
                                              @RequestParam(required = false) String level) {
        Boolean target = enabled != null ? enabled : bodyBoolean(body);
        String targetLevel = level != null ? level : bodyString(body);
        if (target == null && targetLevel == null) {
            throw new BizException(BizCode.PARAM_INVALID, "需提供 enabled 或 level 参数");
        }
        LogLevel logLevel = targetLevel != null ? parseLevel(targetLevel) : (target ? LogLevel.DEBUG : LogLevel.INFO);
        loggingSystem().setLogLevel(loggerName, logLevel);
        log.warn("[cim-jpa] SQL 日志级别已切为 {}（logger={}，来源=HTTP）", logLevel, loggerName);
        return Result.ok(snapshot());
    }

    private Map<String, Object> snapshot() {
        LogLevel current = currentLevel();
        boolean active = current == LogLevel.DEBUG || current == LogLevel.TRACE;

        Map<String, String> dataSources = new LinkedHashMap<>();
        applicationContext.getBeansOfType(DataSource.class).forEach((name, dataSource) ->
                dataSources.put(name, dataSource instanceof SqlLoggingDataSource ? "WRAPPED" : "PLAIN"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("logger", loggerName);
        data.put("level", current.name());
        data.put("active", active);
        data.put("maxSqlLength", maxSqlLength);
        data.put("dataSources", dataSources);
        if (!active) {
            data.put("usage", "POST " + PATH + "?enabled=true 开启；?enabled=false 关闭");
        }
        if (dataSources.containsValue("PLAIN")) {
            data.put("note", "存在未装代理的数据源（cim.jpa.sql-log.enabled=false），切换级别对其无效");
        }
        return data;
    }

    private LogLevel currentLevel() {
        LoggerConfiguration configuration = loggingSystem().getLoggerConfiguration(loggerName);
        if (configuration != null && configuration.getEffectiveLevel() != null) {
            return configuration.getEffectiveLevel();
        }
        return LoggerFactory.getLogger(loggerName).isDebugEnabled() ? LogLevel.DEBUG : LogLevel.INFO;
    }

    private static LogLevel parseLevel(String text) {
        try {
            return LogLevel.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "非法日志级别：" + text + "（可选 TRACE/DEBUG/INFO/WARN/ERROR/OFF）");
        }
    }

    private static LoggingSystem loggingSystem() {
        return LoggingSystem.get(SqlLogAdminController.class.getClassLoader());
    }

    private static Boolean bodyBoolean(Map<String, Object> body) {
        Object value = body == null ? null : body.get("enabled");
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String bodyString(Map<String, Object> body) {
        Object value = body == null ? null : body.get("level");
        return value == null ? null : String.valueOf(value);
    }
}
