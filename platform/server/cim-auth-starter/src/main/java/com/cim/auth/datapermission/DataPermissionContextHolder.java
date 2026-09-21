package com.cim.auth.datapermission;

/**
 * 数据权限上下文的线程持有者（design.md §2.4 / T3.6）。
 *
 * <p>由 {@link DataPermissionAspect} 在注解方法执行前后 set/clear；
 * Repository 构建查询时通过 {@link DataPermissionSpec#apply()} 读取。</p>
 */
public final class DataPermissionContextHolder {

    private static final ThreadLocal<DataPermissionContext> TL = new ThreadLocal<>();

    public static void set(DataPermissionContext ctx) {
        TL.set(ctx);
    }

    public static DataPermissionContext get() {
        return TL.get();
    }

    public static void clear() {
        TL.remove();
    }

    private DataPermissionContextHolder() {
    }
}
