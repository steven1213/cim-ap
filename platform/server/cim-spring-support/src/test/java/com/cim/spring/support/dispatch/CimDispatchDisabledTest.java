package com.cim.spring.support.dispatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 消息分发入口在开关**关闭**时的行为——这是「库 jar 里的端点」最容易翻车的一侧。
 *
 * <p>{@code @RestController} 蕴含 {@code @Component}，宿主 {@code scanBasePackages="com.cim"}
 * 必扫到它。若类上没有与自动装配一字不差的 {@code @ConditionalOnProperty}，开关一关就是
 * 「扫描定义在、依赖 {@code CimMessageDispatcher} 的 @Bean 不在」→ 启动直接失败，
 * 而报错（{@code No qualifying bean of type '...CimMessageDispatcher'}）完全不提开关。</p>
 *
 * <p>所以这两个用例是同一个故障的两面：① 上下文能起来；② 端点是 404。</p>
 */
@SpringBootTest(classes = CimDispatchDisabledTest.DisabledApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "cim.api.dispatch.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CimDispatchDisabledTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationContext ctx;

    @Test
    @DisplayName("开关关闭 + 宿主扫描本包 → 上下文仍能启动（类级 @ConditionalOnProperty 生效）")
    void contextStarts() {
        assertThat(ctx).isNotNull();
    }

    @Test
    @DisplayName("开关关闭 → 分发入口既不是 Bean，也没有路由")
    void endpointIsAbsent() {
        assertThat(ctx.getBeanNamesForType(CimDispatchController.class)).isEmpty();
        ResponseEntity<String> res = rest.postForEntity(
                "/api/v1/messages/__any__", null, String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    @SpringBootApplication(scanBasePackages = "com.cim.spring.support.dispatch")
    static class DisabledApp {
    }
}
