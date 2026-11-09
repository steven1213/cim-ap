package com.cim.jpa.it;

import com.cim.jpa.sqllog.SqlLoggingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多库场景：主库与 {@code cim.jpa.datasources.*} 生成的额外库都应被 SQL 日志代理，
 * 且各自底层仍是独立连接池。
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:sqllog_multi_primary;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "cim.jpa.datasources.second.url=jdbc:h2:mem:sqllog_multi_second;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "cim.jpa.datasources.second.username=sa",
        "cim.jpa.datasources.second.driver-class-name=org.h2.Driver",
        "cim.jpa.datasources.second.dialect=org.hibernate.dialect.H2Dialect",
        "cim.jpa.datasources.second.packages-to-scan=com.cim.jpa.it",
        "cim.jpa.datasources.second.ddl-auto=create-drop",
        "cim.jpa.sql-log.enabled=true",
        "logging.level.cim.jpa.sql=DEBUG"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SqlLoggingMultiDataSourceTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    @Qualifier("secondDataSource")
    private DataSource secondDataSource;

    @Test
    @DisplayName("主库与额外库均被代理，底层连接池保持独立")
    void wrapsPrimaryAndSecondaryDataSources() {
        assertThat(dataSource).isInstanceOf(SqlLoggingDataSource.class);
        assertThat(secondDataSource).isInstanceOf(SqlLoggingDataSource.class);

        DataSource primaryTarget = ((DelegatingDataSource) dataSource).getTargetDataSource();
        DataSource secondTarget = ((DelegatingDataSource) secondDataSource).getTargetDataSource();
        assertThat(primaryTarget).isInstanceOf(HikariDataSource.class);
        assertThat(secondTarget).isInstanceOf(HikariDataSource.class).isNotSameAs(primaryTarget);
    }
}
