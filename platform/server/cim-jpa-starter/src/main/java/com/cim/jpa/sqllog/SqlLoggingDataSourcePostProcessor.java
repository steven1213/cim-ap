package com.cim.jpa.sqllog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;

/**
 * 把所有 {@link DataSource} Bean（含 Spring Boot 主数据源与
 * {@code cim.jpa.datasources.*} 生成的额外库）包成 {@link SqlLoggingDataSource}，
 * 使 SQL 可以「参数已内联」的形式打到指定 logger（见 design.md §2.3）。
 *
 * <p>包装本身只决定「能不能动态打开」；是否真的输出由该 logger 的级别实时决定，
 * 因此 {@code enabled=false} 才是「零开销且不可开启」。</p>
 */
public class SqlLoggingDataSourcePostProcessor implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(SqlLoggingDataSourcePostProcessor.class);

    private final boolean enabled;
    private final String loggerName;
    private final int maxSqlLength;

    public SqlLoggingDataSourcePostProcessor(boolean enabled, String loggerName, int maxSqlLength) {
        this.enabled = enabled;
        this.loggerName = loggerName;
        this.maxSqlLength = maxSqlLength;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!enabled || bean instanceof SqlLoggingDataSource || !(bean instanceof DataSource dataSource)) {
            return bean;
        }
        LOG.info("[cim-jpa] DataSource '{}' 已装 SQL 日志代理（logger={}，当前级别={}；"
                        + "切成 DEBUG 即输出可执行 SQL，可运行时切换）",
                beanName, loggerName, LoggerFactory.getLogger(loggerName).isDebugEnabled() ? "DEBUG" : "非 DEBUG");
        return new SqlLoggingDataSource(dataSource, loggerName, maxSqlLength);
    }
}
