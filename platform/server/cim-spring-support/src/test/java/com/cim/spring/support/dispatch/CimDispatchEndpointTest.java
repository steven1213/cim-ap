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
 * 消息分发入口在开关**打开**时的装配。
 *
 * <p>存在的理由：这个入口此前是「默认关 + 零测试」。它用的是
 * {@code @RequestMapping + @ResponseBody} 而非 {@code @RestController}（注释里还写着
 * 「仍被 MVC 识别为处理器」），而 Spring Framework 6.0 起 {@code isHandler()} 只认
 * {@code @Controller}（见 {@code LibraryControllerAssemblyTest} 的第 2 条结论）——
 * 也就是说开关一旦有人打开，端点会静默 404。这条测试把它钉住。</p>
 */
@SpringBootTest(classes = CimDispatchEndpointTest.EnabledApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "cim.api.dispatch.enabled=true",
                // 探针路径未注册，走的是 BizException 分支；本测试只验「路由是否存在」
                "cim.api.response.verify=false",
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CimDispatchEndpointTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationContext ctx;

    @Test
    @DisplayName("开关打开 → 分发入口注册为 Bean（扫描定义与 @Bean 不会冲突）")
    void controllerIsRegistered() {
        assertThat(ctx.getBeanNamesForType(CimDispatchController.class)).isNotEmpty();
    }

    @Test
    @DisplayName("开关打开 → POST /api/v1/messages/{name} 可路由（未注册的消息也是业务错误，不是 404）")
    void endpointIsMapped() {
        ResponseEntity<String> res = rest.postForEntity(
                "/api/v1/messages/__not_registered__", null, String.class);

        // 关键：404 代表「容器里根本没有这个处理器」，正是 Spring 6 + @RequestMapping 的症状。
        // 未注册的消息名会抛 PARAM_INVALID，映射成 400 —— 那才说明路由活着。
        assertThat(res.getStatusCode().value())
                .as("404 说明分发入口又变回了 Spring 6 不认的注解写法（必须 @RestController）")
                .isNotEqualTo(404);
    }

    /**
     * 扫描根指向本包：{@code CimDispatchController} 会被组件扫描拾取，
     * 与自动装配的 {@code @Bean} 同名同类型——这正是宿主应用的实际情况。
     */
    @SpringBootApplication(scanBasePackages = "com.cim.spring.support.dispatch")
    static class EnabledApp {
    }
}
