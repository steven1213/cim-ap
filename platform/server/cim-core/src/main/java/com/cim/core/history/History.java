package com.cim.core.history;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明实体的历史策略，由 {@code cim-jpa-starter} 的 {@code HistoryInterceptor} 在写操作时
 * **自动触发**记录，业务无需手写历史逻辑（见 README §9）。
 *
 * <pre>{@code
 * @History(SNAPSHOT)                         // 整行快照 → {X}Hist
 * @History(STATE_LOG)                        // 状态变迁 → {X}StateLog
 * @History(value = SNAPSHOT, include = {"code", "name"})  // 仅追踪白名单字段
 * }</pre>
 *
 * <p>无论哪种策略，历史都落在该实体**专属**的历史表中。</p>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface History {

    /** 历史策略，默认整行快照。 */
    HistoryStrategy value() default HistoryStrategy.SNAPSHOT;

    /** 仅追踪这些字段（白名单），为空表示全部业务字段。 */
    String[] include() default {};

    /** 忽略这些字段（黑名单），优先级高于 {@link #include()}。 */
    String[] exclude() default {};
}
