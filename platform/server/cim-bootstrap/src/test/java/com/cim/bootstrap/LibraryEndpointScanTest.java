package com.cim.bootstrap;

import com.cim.jpa.sqllog.SqlLogAdminController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「宿主扫描 vs 库内控制器开关」的守门测试。
 *
 * <p><b>它守的是什么</b>：{@code cim-bootstrap} 用 {@code scanBasePackages="com.cim"} 覆盖所有 starter，
 * 而 starter 里的控制器都带 {@code @RestController}（= {@code @Component}）。于是组件扫描会
 * <b>绕过自动装配的条件</b>直接注册它们；一旦这些端点依赖的 Bean 因为开关关闭而不存在，
 * 启动就失败——报错却只说「缺某个类型」，完全看不出根因是「你把某个开关关了」。</p>
 *
 * <p>实测形态（{@code cim.jpa.sql-log.admin-enabled=false}）：
 * {@code No qualifying bean of type 'java.lang.String' available}——因为
 * {@code SqlLogAdminController} 的构造器第一个参数是 {@code loggerName}。前后启动两次才定位到。</p>
 *
 * <p>修法是把同一个条件写到控制器类上，扫描阶段即跳过。本测试用<b>关闭开关的真实上下文</b>来钉住它：
 * 上下文能加载 + 该 Bean 不存在。</p>
 */
@SpringBootTest(classes = CimApApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "cim.jpa.sql-log.admin-enabled=false",
                "spring.datasource.url=jdbc:h2:mem:bootstrap_scan;DB_CLOSE_DELAY=-1;MODE=MySQL",
        })
class LibraryEndpointScanTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    @DisplayName("开关关闭时：上下文能启动，且端点 Bean 未被组件扫描偷偷注册")
    void switchedOffEndpointIsNotScannedIn() {
        // 只要这里拿到 Bean，说明类上少了 @ConditionalOnProperty —— 而它的构造参数没人能注入，
        // 换到别的宿主（构造器不同）也可能"能启动但端点意外暴露"，两种都不对。
        assertThat(ctx.getBeanNamesForType(SqlLogAdminController.class))
                .as("sql-log 开关关闭时端点不该存在于容器（类需带与自动装配一致的 @ConditionalOnProperty）")
                .isEmpty();
    }
}
