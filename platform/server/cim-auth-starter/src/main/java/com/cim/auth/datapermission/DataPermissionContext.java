package com.cim.auth.datapermission;

/**
 * 数据权限上下文（一次请求内有效），由 {@link DataPermissionAspect} 写入。
 */
public class DataPermissionContext {

    /** 作用域。 */
    public Scope scope = Scope.TENANT;

    /** 当前用户 ID。 */
    public String userId;

    /** 租户 ID（TENANT 作用域）。 */
    public String tenantId;

    /** 部门 ID（DEPT 作用域）。 */
    public String deptId;

    /** 工厂 ID（FACTORY 作用域）。 */
    public String factoryId;

    /** 是否超级管理员（短路放通，不过滤）。 */
    public boolean isSuper;
}
