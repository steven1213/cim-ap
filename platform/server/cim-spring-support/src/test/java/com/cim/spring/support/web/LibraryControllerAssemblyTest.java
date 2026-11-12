package com.cim.spring.support.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「库 jar 里的 HTTP 端点该怎么装配」的守门测试。
 *
 * <p><b>为什么需要它</b>：这条约定此前只写在文档里，而且描述的失败（去掉 {@code @RestController}
 * 就 404）无法复现——于是没人知道到底哪种写法能跑。实际后果是 {@code CimDispatchController}
 * 用了同一种「未验证」的写法、默认关闭、零测试，等于把风险留到第一次有人打开开关。</p>
 *
 * <p>本测试用**真实 MVC 装配**（不是 {@code standaloneSetup}：那种方式直接传实例，绕过
 * 「Bean 定义 → HandlerMapping 发现」这一步，正好把这个问题的关键环节跳过了）
 * 对两种写法各打一次真实 HTTP。</p>
 *
 * <p><b>结论</b>（断言即文档，改代码前先读）：
 * <ol>
 *   <li>{@code @RestController} + {@code @Bean} 注册 → 可路由；</li>
 *   <li><b>{@code @RequestMapping + @ResponseBody} + {@code @Bean} 注册 → 404</b>。
 *       这不是"忘了注册"，而是一条版本断层：{@code RequestMappingHandlerMapping.isHandler()}
 *       在 <b>Spring Framework 6.0</b> 起只认 {@code @Controller}（反编译 6.1.14 确认：
 *       方法体只剩一句 {@code hasAnnotation(beanType, Controller.class)}），
 *       5.x 时代的「或 {@code @RequestMapping}」分支已被删除。
 *       所以想被 MVC 发现，<b>类上必须有 {@code @Controller}（或 {@code @RestController}）</b>。</li>
 *   <li>而 {@code @Controller} 蕴含 {@code @Component} → 宿主扫描一定会扫到它。
 *       于是「库内端点」真正的问题不是注解怎么选，而是<b>开关关闭时扫描会把端点拉起来</b>。
 *       解法：类上加与自动装配<b>一模一样</b>的 {@code @ConditionalOnProperty}，
 *       让扫描阶段就跳过（见 {@code SqlLogAdminController} 与 {@code SseController}，
 *       以及 {@code cim-bootstrap} 的 {@code LibraryEndpointScanTest}）。</li>
 *   <li>不注册（哪怕类上有 {@code @RequestMapping}）→ 404，与第 2 条表现相同但原因不同：
 *       第 2 条是框架不认，这条是容器里根本没有这个 Bean。</li>
 * </ol>
 */
@SpringBootTest(classes = LibraryControllerAssemblyTest.ProbeApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // 探针控制器返回裸 Map，不满足统一响应契约；本测试只验路由可达
        properties = "cim.api.response.verify=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LibraryControllerAssemblyTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("@RestController + @Bean 注册 → 可路由（SseController 用的就是这种）")
    void beanRegisteredRestControllerIsMapped() {
        ResponseEntity<String> res = rest.getForEntity("/probe/rest", String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).contains("\"style\":\"rest\"");
    }

    @Test
    @DisplayName("@RequestMapping + @ResponseBody + @Bean 注册 → 404（Spring 6 起 isHandler 只认 @Controller）")
    void beanRegisteredPlainMappingIsNotMapped() {
        ResponseEntity<String> res = rest.getForEntity("/probe/plain", String.class);
        assertThat(res.getStatusCode().value())
                .as("若这里变成 200，说明 Spring 又认 @RequestMapping 了——"
                        + "先复查 RequestMappingHandlerMapping.isHandler 再改文档，"
                        + "不要顺手把库内控制器的 @RestController 去掉")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("仅类上有 @RequestMapping、没有 Bean 注册 → 404（去注解就得自己注册）")
    void unregisteredMappingIsNotMapped() {
        // 这个探针类既没有 @Component 也没有 @Bean —— 只被 new 出来过，从未进入容器
        ResponseEntity<String> res = rest.getForEntity("/probe/orphan", String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }

    /** 探针应用：扫描根指向不存在的包，只装配本测试显式声明的 Bean。 */
    @SpringBootApplication(scanBasePackages = "com.cim.spring.support.probe.empty")
    @Import(Probes.class)
    static class ProbeApp {
    }

    @Configuration
    static class Probes {
        @Bean
        RestStyleController restStyleController() {
            return new RestStyleController();
        }

        @Bean
        PlainStyleController plainStyleController() {
            return new PlainStyleController();
        }

        // OrphanController 刻意**不**在这里注册：证明「有 @RequestMapping 但没进容器」= 404。
        // 若哪天有人把它加进来，第三个用例会转红——这正是想要的报警。
    }

    @RestController
    @RequestMapping("/probe/rest")
    static class RestStyleController {
        @GetMapping
        public Map<String, Object> get() {
            return Map.of("style", "rest");
        }
    }

    @RequestMapping("/probe/plain")
    @ResponseBody
    static class PlainStyleController {
        @GetMapping
        public Map<String, Object> get() {
            return Map.of("style", "plain");
        }
    }

    @RequestMapping("/probe/orphan")
    @ResponseBody
    static class OrphanController {
        @GetMapping
        public Map<String, Object> get() {
            return Map.of("style", "orphan");
        }
    }
}
