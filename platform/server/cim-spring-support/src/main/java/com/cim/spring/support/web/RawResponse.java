package com.cim.spring.support.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明「本端点刻意不套 {@link Result} 信封」，是统一响应契约的**唯一例外出口**。
 *
 * <p>规则见 {@code docs/platform/server/api-convention.md §5}：控制器方法的返回类型要么是
 * {@link Result}，要么标注本注解并给出 {@link Reason}。没有第三种情况——启动期由
 * {@link ApiContractVerifier} 校验，违反即失败（生产可降级为告警，见
 * {@code cim.api.response.strict}）。</p>
 *
 * <p><b>为什么必须有理由枚举</b>：例外本身不是问题，「说不清为什么例外」才是。枚举把理由收敛成
 * 四类可审计的口径，评审时只需判断「这条属于哪一类」，而不是逐条重新论证。</p>
 *
 * @see ApiContractVerifier
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RawResponse {

    /** 例外类别（必填）。 */
    Reason reason();

    /** 补充说明（会出现在启动期清单里，便于审计；建议写清「谁消费、为什么不能包」）。 */
    String value() default "";

    /** 例外类别——只有这四类被认可。 */
    enum Reason {

        /** 二进制/文件下载：{@code Content-Disposition: attachment}，信封会破坏文件本身。 */
        DOWNLOAD,

        /** 服务端渲染页面或 302 跳转：{@code text/html} 或 {@code Location}，不是数据接口。 */
        HTML,

        /** 标准协议端点：报文体由外部规范规定（如 JWKS 的 RFC 7517），改动即不兼容。 */
        STANDARD_PROTOCOL,

        /** 流式响应：SSE / WebSocket，长连接上无法再套一次性信封。 */
        STREAM
    }
}
