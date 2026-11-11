package com.cim.spring.support.dispatch;

import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把一个请求 DTO 挂到「消息分发入口」上（模式说明见 {@code api-convention.md §7}）。
 *
 * <p>标注在**请求 DTO** 上而不是控制器上：分发的本质是「一个 DTO 对应一个处理器」，
 * 让 DTO 自己声明归属，新增一种消息 = 新增一个 DTO + 一个处理器，零控制器改动。</p>
 *
 * <p>与参考实现（{@code com.ameth...AutoMsgToProcessor}）的差异：参考实现要求处理器
 * 自己返回 {@code Result}，漏包就会破坏契约；本实现由 {@link CimMessageDispatcher}
 * 统一包信封，处理器只返回业务数据，**结构上没有漏包的可能**。</p>
 *
 * @see CimMessageProcessor
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CimMessage {

    /**
     * 分发路径（可选）。留空时由请求类名派生：去掉 {@code Request} 后缀、首字母小写，
     * 例如 {@code LeaveApplyRequest → /leaveApply}。比较时大小写不敏感。
     */
    String path() default "";

    /** 处理该消息的处理器 Bean 类型（必须实现 {@link CimMessageProcessor}）。 */
    Class<? extends CimMessageProcessor<?>> processor();

    /** 允许的 HTTP 方法，默认仅 POST（消息投递语义，不做查询复用）。 */
    RequestMethod method() default RequestMethod.POST;

    /** 是否启用；置 {@code false} 可临时下线某类消息而不删代码。 */
    boolean enabled() default true;
}
