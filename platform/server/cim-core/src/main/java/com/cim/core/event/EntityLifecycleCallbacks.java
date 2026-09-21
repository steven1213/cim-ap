package com.cim.core.event;

/**
 * 生命周期回调的静态持有者（注册点）。
 *
 * <p>由 {@code cim-jpa-starter} 在启动时调用 {@link #register(EntityLifecycleCallback)}
 * 注入 Spring 管理的实现；未注册时为空实现（保证纯领域单测无需基础设施）。</p>
 */
public final class EntityLifecycleCallbacks {

    private static volatile EntityLifecycleCallback callback = new NoopCallback();

    private EntityLifecycleCallbacks() {
    }

    /** 注册回调实现（由基础设施 starter 调用）。 */
    public static void register(EntityLifecycleCallback impl) {
        callback = (impl != null) ? impl : new NoopCallback();
    }

    /** 插入前回调。 */
    public static void beforeInsert(Object entity) {
        callback.beforeInsert(entity);
    }

    /** 更新前回调。 */
    public static void beforeUpdate(Object entity) {
        callback.beforeUpdate(entity);
    }

    /** 删除前回调。 */
    public static void beforeDelete(Object entity) {
        callback.beforeDelete(entity);
    }

    /** 空实现。 */
    static final class NoopCallback implements EntityLifecycleCallback {
        @Override
        public void beforeInsert(Object entity) {
            // no-op
        }

        @Override
        public void beforeUpdate(Object entity) {
            // no-op
        }
    }
}
