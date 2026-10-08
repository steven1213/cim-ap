package com.cim.system.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明「该方法的调用记入操作日志」（design.md §2.10 / plan.md T6.4）。
 *
 * <p>与 {@code cim-obs-starter} 的指标/日志门面互补：本注解面向<b>业务流水落库</b>
 * （{@code sys_operation_log}，可供管理员在界面审计），而非技术日志。</p>
 *
 * <p>刻意只记录 module/action/target/URI/HTTP 方法/耗时/成功与否——<b>不落参数与返回值</b>，
 * 这是默认脱敏策略（同 {@link OperationLog} 的说明）。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLogged {

    /** 模块名（如 {@code sys}、{@code mes}）。 */
    String module() default "sys";

    /** 动作名（如 {@code save}、{@code remove}）。 */
    String action();
}
