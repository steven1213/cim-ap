package com.cim.core.event;

/**
 * 实体生命周期回调（由 {@code cim-jpa-starter} 提供实现，用于注入主键、填充审计字段、
 * 记录历史等）。
 *
 * <p>{@code cim-core} 通过 {@link EntityLifecycleCallbacks} 静态持有回调实现，
 * 从而在 **零 Spring 依赖** 的前提下让 JPA {@code @PrePersist}/{@code @PreUpdate}
 * 回调业务逻辑（见 design.md §1.1 的调和说明）。</p>
 */
public interface EntityLifecycleCallback {

    /** 插入前（生成主键，填充 create / event 系列字段与 tenant）。 */
    void beforeInsert(Object entity);

    /** 更新前（填充 event 系列字段与 tenant）。 */
    void beforeUpdate(Object entity);

    /** 删除前（默认无操作）。 */
    default void beforeDelete(Object entity) {
        // no-op
    }
}
