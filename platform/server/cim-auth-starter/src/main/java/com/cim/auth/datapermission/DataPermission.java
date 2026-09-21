package com.cim.auth.datapermission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解（design.md §4.4 / T3.6）。
 *
 * <p>声明在 Service 方法上，由 {@link DataPermissionAspect} 织入作用域上下文，
 * 供 Repository 侧 {@link DataPermissionSpec} 构造过滤 Specification。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /** 数据权限作用域，默认按租户。 */
    Scope value() default Scope.TENANT;
}
