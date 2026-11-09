package com.cim.jpa.sqllog;

import com.cim.spring.support.web.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET/POST /sys/sql-log} 的 HTTP 契约。 */
class SqlLogAdminControllerTest {

    private static final String LOGGER = "cim.jpa.sql.http.test";

    private AnnotationConfigApplicationContext context;
    private SqlLogAdminController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.refresh();
        controller = new SqlLogAdminController(LOGGER, 4000, context);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        loggingSystem().setLogLevel(LOGGER, LogLevel.INFO);
    }

    @AfterEach
    void tearDown() {
        loggingSystem().setLogLevel(LOGGER, LogLevel.INFO);
        context.close();
    }

    @Test
    @DisplayName("GET 返回当前级别与数据源代理状态")
    void statusReportsCurrentState() throws Exception {
        mvc.perform(get(SqlLogAdminController.PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.logger").value(LOGGER))
                .andExpect(jsonPath("$.data.level").value("INFO"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.maxSqlLength").value(4000))
                .andExpect(jsonPath("$.data.dataSources").isMap());
    }

    @Test
    @DisplayName("POST ?enabled=true/false 切换级别")
    void togglesByQueryParam() throws Exception {
        mvc.perform(post(SqlLogAdminController.PATH).param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.level").value("DEBUG"))
                .andExpect(jsonPath("$.data.active").value(true));

        mvc.perform(post(SqlLogAdminController.PATH).param("enabled", "false"))
                .andExpect(jsonPath("$.data.level").value("INFO"))
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("POST JSON body 与 level 参数同样生效")
    void togglesByJsonBodyAndLevel() throws Exception {
        mvc.perform(post(SqlLogAdminController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(jsonPath("$.data.level").value("DEBUG"));

        mvc.perform(post(SqlLogAdminController.PATH).param("level", "trace"))
                .andExpect(jsonPath("$.data.level").value("TRACE"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("非法级别与空参数按业务码拒绝")
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> controller.change(null, null, "LOUD"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("LOUD");
        assertThatThrownBy(() -> controller.change(null, null, null))
                .isInstanceOf(BizException.class);
        assertThat(loggingSystem().getLoggerConfiguration(LOGGER).getEffectiveLevel())
                .isEqualTo(LogLevel.INFO);
    }

    private static LoggingSystem loggingSystem() {
        return LoggingSystem.get(SqlLogAdminControllerTest.class.getClassLoader());
    }
}
