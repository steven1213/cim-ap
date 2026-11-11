package com.cim.spring.support.dispatch;

import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
 * <p><b>为什么不用 {@code @RestController}</b>：它是 {@code @Component} 语义。宿主应用若
 * {@code scanBasePackages="com.cim"}（装配型应用就是），会把本类当普通组件扫进来，而自动装配
 * 关闭时 {@link CimMessageDispatcher} 并不存在 → 启动失败。这里改用
 * {@code @RequestMapping + @ResponseBody}：仍被 MVC 识别为处理器，但不是组件，只能由
 * {@code CimDispatchAutoConfiguration} 显式注册，**「启用才有入口」由此在结构上成立**。</p>
 *
 * <p><b>安全</b>：本入口不自行放行，落在应用既有的鉴权过滤链之内；开启它的应用必须确认
 * 权限模型覆盖该前缀。</p>
 */
@RequestMapping("${cim.api.dispatch.base-path:/api/v1/messages}")
@ResponseBody
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
