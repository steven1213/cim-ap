package com.cim.mq.listener;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个集成事件消费方法。
 *
 * <p>标注在 Spring Bean 的方法上，方法签名支持 {@code (IntegrationEvent)} 或
 * {@code (IntegrationEvent, Acknowledgment)}。框架在容器刷新后自动注册到
 * {@code MqTemplate}，并包裹幂等/重试/死信。</p>
 *
 * <pre>{@code
 * @MqListener(topic = "order-created", group = "inventory")
 * public void on(IntegrationEvent e) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MqListener {

    /** 订阅主题（必填）。 */
    String topic();

    /** 消费组（同一组负载均衡，不同组广播）。 */
    String group() default "default";

    /** 并发消费线程数。 */
    int concurrency() default 1;

    /** 最大重试次数（超出转死信）。 */
    int maxAttempts() default 3;
}
