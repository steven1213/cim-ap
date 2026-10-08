package com.cim.cache.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明方法执行后使缓存失效（Cache-Aside 写后删缓存，而非更新缓存，避免并发写乱序）。
 *
 * <pre>
 * &#64;CacheEvictCim(value = "equipment", key = "#entity.id")
 * public Equipment save(Equipment entity) { return repo.save(entity); }
 *
 * // 批量/全量变更时清空整个命名空间
 * &#64;CacheEvictCim(value = "equipment", allEntries = true)
 * public void reloadAll() { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheEvictCim {

    /** 命名空间（缓存名），与 {@link CacheableCim#value()} 对应。 */
    String value();

    /** 键的 SpEL 表达式；{@link #allEntries()} 为 true 时忽略。 */
    String key() default "";

    /** 是否清空整个命名空间（用于批量/全量变更）。默认 false。 */
    boolean allEntries() default false;
}
