package com.cim.jpa.sqllog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 把实际执行的 SQL（参数已内联）以 DEBUG 打到指定 logger 的数据源包装器。
 *
 * <p><b>开关是运行时的</b>：是否建代理由 {@code getConnection()} 时的
 * {@code logger.isDebugEnabled()} 决定，因此把该 logger 切成 DEBUG/TRACE 后
 * **下一次取连接**即开始输出，切回 INFO 即静默——无需重启（可用
 * {@code POST /sys/sql-log} 或 actuator {@code /actuator/loggers} 触发）。
 * 关闭状态下不创建任何代理，仅有「每连接一次级别判断」的开销。</p>
 *
 * <p>开启后拦截 {@link PreparedStatement}/{@link CallableStatement}（参数在 {@code setXxx} 时记录）
 * 与无参 {@link Statement}（校验查询、DDL、原生 SQL），在 {@code execute*} 返回后按
 * 「耗时 + 可执行 SQL」输出一行。</p>
 *
 * <p>继承 {@link DelegatingDataSource} 是为了让 Spring Boot 的健康检查/连接池指标
 * 仍能 {@code unwrap} 到底层 {@code HikariDataSource}；{@link #close()} 用于 Bean 销毁阶段
 * 关闭真实连接池（Boot 为 {@code HikariDataSource} 推断的 destroy 方法名是 {@code close}）。</p>
 */
public class SqlLoggingDataSource extends DelegatingDataSource {

    /** 会真正执行 SQL 的方法名。 */
    private static final Set<String> EXECUTIONS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate",
            "executeBatch", "executeLargeBatch");

    private final Logger logger;
    private final int maxSqlLength;

    public SqlLoggingDataSource(DataSource targetDataSource, String loggerName, int maxSqlLength) {
        super(targetDataSource);
        this.logger = LoggerFactory.getLogger(loggerName);
        this.maxSqlLength = maxSqlLength > 0 ? maxSqlLength : 4000;
    }

    /** 当前是否在输出 SQL（logger 为 DEBUG/TRACE）。 */
    public boolean isLoggingActive() {
        return logger.isDebugEnabled();
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapIfActive(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapIfActive(super.getConnection(username, password));
    }

    /** 关闭真实数据源（Boot 对连接池 Bean 推断的 destroy 方法是 {@code close}）。 */
    public void close() throws SQLException {
        DataSource target = getTargetDataSource();
        if (!(target instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (SQLException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("close target datasource failed", e);
        }
    }

    private Connection wrapIfActive(Connection target) {
        // 关闭时直接返回真实连接：不建代理，语句层零开销；打开后下一次取连接即生效
        if (target == null || !logger.isDebugEnabled()) {
            return target;
        }
        return (Connection) Proxy.newProxyInstance(SqlLoggingDataSource.class.getClassLoader(),
                new Class<?>[]{Connection.class}, new ConnectionHandler(target, logger, maxSqlLength));
    }

    private static Object invokeTarget(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    private static String prefix(long nanos, boolean failed) {
        return String.format("[SQL] %.1fms%s | ", nanos / 1_000_000.0, failed ? " FAILED" : "");
    }

    /** 拦截 {@code prepareStatement}/{@code prepareCall}/{@code createStatement}，其余方法透传。 */
    private static final class ConnectionHandler implements InvocationHandler {

        private final Connection target;
        private final Logger logger;
        private final int maxSqlLength;

        private ConnectionHandler(Connection target, Logger logger, int maxSqlLength) {
            this.target = target;
            this.logger = logger;
            this.maxSqlLength = maxSqlLength;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = invokeTarget(method, target, args);
            String name = method.getName();
            if (result instanceof PreparedStatement statement
                    && ("prepareStatement".equals(name) || "prepareCall".equals(name))) {
                if (args == null || args.length == 0 || !(args[0] instanceof String sql)) {
                    return result;
                }
                Class<?> iface = "prepareCall".equals(name) ? CallableStatement.class : PreparedStatement.class;
                return Proxy.newProxyInstance(SqlLoggingDataSource.class.getClassLoader(),
                        new Class<?>[]{iface}, new StatementHandler(statement, sql, logger, maxSqlLength));
            }
            // 无参 Statement（校验查询 / DDL / 原生 SQL）
            if (result instanceof Statement statement && "createStatement".equals(name)) {
                return Proxy.newProxyInstance(SqlLoggingDataSource.class.getClassLoader(),
                        new Class<?>[]{Statement.class},
                        new PlainStatementHandler(statement, logger, maxSqlLength));
            }
            return result;
        }
    }

    /** 无参 {@link Statement}：SQL 直接取自执行方法的入参。 */
    private static final class PlainStatementHandler implements InvocationHandler {

        private final Statement target;
        private final Logger logger;
        private final int maxSqlLength;
        private final List<String> batchSql = new ArrayList<>(4);

        private PlainStatementHandler(Statement target, Logger logger, int maxSqlLength) {
            this.target = target;
            this.logger = logger;
            this.maxSqlLength = maxSqlLength;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!logger.isDebugEnabled()) {
                return invokeTarget(method, target, args);
            }
            String name = method.getName();
            int argc = args == null ? 0 : args.length;
            if ("addBatch".equals(name) && argc == 1 && args[0] instanceof String sql) {
                batchSql.add(sql);
                return invokeTarget(method, target, args);
            }
            if ("clearBatch".equals(name)) {
                batchSql.clear();
                return invokeTarget(method, target, args);
            }
            if (EXECUTIONS.contains(name)) {
                long start = System.nanoTime();
                try {
                    Object result = invokeTarget(method, target, args);
                    emit(name, System.nanoTime() - start, false, args);
                    return result;
                } catch (Throwable t) {
                    emit(name, System.nanoTime() - start, true, args);
                    throw t;
                }
            }
            return invokeTarget(method, target, args);
        }

        private void emit(String name, long nanos, boolean failed, Object[] args) {
            String prefix = prefix(nanos, failed);
            if ("executeBatch".equals(name) || "executeLargeBatch".equals(name)) {
                for (String sql : batchSql) {
                    logger.debug("{}{}", prefix, SqlLogFormatter.toExecutable(sql, Map.of(), maxSqlLength));
                }
                batchSql.clear();
                return;
            }
            if (args != null && args.length > 0 && args[0] instanceof String sql) {
                logger.debug("{}{}", prefix, SqlLogFormatter.toExecutable(sql, Map.of(), maxSqlLength));
            }
        }
    }

    /** 记录参数并在执行后输出可执行 SQL。 */
    private static final class StatementHandler implements InvocationHandler {

        private final PreparedStatement target;
        private final String sql;
        private final Logger logger;
        private final int maxSqlLength;
        private final Map<Integer, Object> params = new TreeMap<>();
        private final List<Map<Integer, Object>> batches = new ArrayList<>(4);

        private StatementHandler(PreparedStatement target, String sql, Logger logger, int maxSqlLength) {
            this.target = target;
            this.sql = sql;
            this.logger = logger;
            this.maxSqlLength = maxSqlLength;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (!logger.isDebugEnabled()) {
                return invokeTarget(method, target, args);
            }
            String name = method.getName();
            int argc = args == null ? 0 : args.length;

            // setXxx(int, ...) 记录参数；setFetchSize(int) 等单参方法不满足 argc>=2，天然排除
            if (name.startsWith("set") && argc >= 2 && args[0] instanceof Integer index) {
                params.put(index, paramValue(name, args));
                return invokeTarget(method, target, args);
            }
            if ("addBatch".equals(name) && argc == 0) {
                batches.add(new TreeMap<>(params));
                return invokeTarget(method, target, args);
            }
            if ("clearParameters".equals(name)) {
                params.clear();
                return invokeTarget(method, target, args);
            }
            if ("clearBatch".equals(name)) {
                batches.clear();
                return invokeTarget(method, target, args);
            }
            if (EXECUTIONS.contains(name)) {
                long start = System.nanoTime();
                try {
                    Object result = invokeTarget(method, target, args);
                    emit(name, System.nanoTime() - start, false);
                    return result;
                } catch (Throwable t) {
                    emit(name, System.nanoTime() - start, true);
                    throw t;
                }
            }
            return invokeTarget(method, target, args);
        }

        private void emit(String name, long nanos, boolean failed) {
            String prefix = prefix(nanos, failed);
            if (("executeBatch".equals(name) || "executeLargeBatch".equals(name)) && !batches.isEmpty()) {
                for (Map<Integer, Object> batch : batches) {
                    logger.debug("{}{}", prefix, SqlLogFormatter.toExecutable(sql, batch, maxSqlLength));
                }
                batches.clear();
                return;
            }
            logger.debug("{}{}", prefix, SqlLogFormatter.toExecutable(sql, params, maxSqlLength));
        }

        private static Object paramValue(String name, Object[] args) {
            if ("setNull".equals(name)) {
                return null;
            }
            return switch (name) {
                case "setBinaryStream", "setAsciiStream", "setCharacterStream", "setUnicodeStream",
                     "setNCharacterStream", "setBlob", "setClob", "setNClob", "setArray", "setRef" ->
                        "<" + name.substring(3).toLowerCase() + ">";
                default -> args[1];
            };
        }
    }
}
