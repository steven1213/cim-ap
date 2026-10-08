package com.cim.cache.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明方法结果可缓存（多级缓存：本地 Caffeine + Redis）。
 *
 * <p>等价于 Spring 的 {@code @Cacheable}，但走本框架的多级缓存与三守卫（防穿透/击穿/雪崩）。
 * 命中顺序：L1 → L2 → 回源并回填。回源在互斥锁保护下进行（防击穿）。</p>
 *
 * <pre>
 * &#64;CacheableCim(value = "equipment", key = "#id", ttl = 600)
 * public Equipment load(String id) { return repo.findById(id); }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheableCim {

    /** 命名空间（缓存名），也是键的前缀段，建议与业务实体对应。 */
    String value();

    /** 键的 SpEL 表达式（见 {@link CacheKeyGenerator}）。为空时按参数拼接。 */
    String key() default "";

    /** L2 TTL（秒）；0 表示使用全局默认（{@code cim.cache.redis.default-ttl}）。 */
    long ttl() default 0;

    /** 是否互斥重建（防击穿）。默认开启；关闭则并发未命中会同时回源。 */
    boolean sync() default true;
}
