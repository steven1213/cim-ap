package com.cim.spring.support.dispatch;

import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息分发入口（**可选**，由 {@code cim.api.dispatch.enabled=true} 装配）。
 *
 * <p>形态与参考实现的 {@code CustomAutoProcessorController} 一致：一个通配入口承载多品类请求，
 * 路径第二段即消息名（{@code POST /api/v1/messages/leaveApply}）。</p>
 *
 * <p>刻意把入口收窄成「单段路径 + POST」：参考实现同时挂了
 * {@code /{controller}Controller/{request}Request} 与 {@code /auto/{request}Request} 两套通配，
 * 前者要求调用方按内部类名拼 URL，等于把实现细节写进了对外契约。这里只保留按消息名寻址。</p>
 *
 * <h3>为什么是「@RestController + 类级开关」而不是「@RequestMapping + @Bean 注册」</h3>
 *
 * <p>本类曾用 {@code @RequestMapping + @ResponseBody}（意图：不标 {@code @RestController}
 * 就不是组件，开关关闭时不会被宿主扫描到，只能由自动装配显式注册）。<b>这个写法在
 * Spring Framework 6.0 起必然 404</b>：{@code RequestMappingHandlerMapping.isHandler()}
 * 只剩 {@code hasAnnotation(beanType, Controller.class)} 一条判定，5.x 里那个
 * 「或者标了 @RequestMapping」的分支已被删除。也就是说入口虽然注册成了 Bean，
 * 却永远不会被当成处理器——而且没有任何报错，只有调用时 404。</p>
 *
 * <p>而 {@code @RestController} 蕴含 {@code @Component}，宿主 {@code scanBasePackages="com.cim"}
 * 一定会扫到它；此时若只靠自动装配的 {@code @Bean} 提供依赖，开关一关就会出现
 * 「扫描定义存在、@Bean 不注册」→ 构造器参数缺失，启动失败且报错不提开关。所以类上必须
 * 再挂一份与自动装配**一字不差**的 {@code @ConditionalOnProperty}。三件套的完整规则见
 * {@code docs/platform/server/api-convention.md §6.5}。</p>
 *
 * <p><b>安全</b>：本入口不自行放行，落在应用既有的鉴权过滤链之内；开启它的应用必须确认
 * 权限模型覆盖该前缀。</p>
 */
@RestController
@RequestMapping("${cim.api.dispatch.base-path:/api/v1/messages}")
@ConditionalOnProperty(prefix = "cim.api.dispatch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CimDispatchController {

    private final CimMessageDispatcher dispatcher;

    /** 投递一条消息。报文由 {@link CimMessage} 标注的 DTO 反序列化。 */
    @PostMapping("/{request}")
    public Result<?> post(@PathVariable("request") String request,
                          @RequestBody(required = false) String body) {
        return dispatcher.dispatch("/" + request, body);
    }
}
