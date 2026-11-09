package com.cim.jpa.it;

import com.cim.jpa.sqllog.SqlLoggingDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认行为：{@code cim.jpa.sql-log.enabled} 缺省 {@code true}，即使 logger 是 INFO 也装代理
 * ——这是「运行时可动态打开」的前提；INFO 级别下不产生任何输出。
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:sqllog_default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SqlLoggingDefaultWrapTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("未做任何配置时数据源已装代理，默认级别下静默")
    void wrapsByDefaultAndStaysSilent() {
        assertThat(dataSource).isInstanceOf(SqlLoggingDataSource.class);
        assertThat(((SqlLoggingDataSource) dataSource).isLoggingActive()).isFalse();
    }
}
