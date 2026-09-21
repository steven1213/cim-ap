package com.cim.auth.datapermission;

/**
 * 数据权限作用域（design.md §4.4 / T3.6）。
 *
 * <p>业务方法标注 {@code @DataPermission(Scope.X)} 后，切面将当前操作人的作用域上下文
 * 写入 {@link DataPermissionContextHolder}，Repository 构建查询时通过
 * {@link DataPermissionSpec#apply()} 织入对应过滤条件（零 SQL 拼接）。</p>
 */
public enum Scope {

    /** 按工厂维度隔离。 */
    FACTORY,

    /** 按部门维度隔离。 */
    DEPT,

    /** 仅本人数据（create_user = 当前用户）。 */
    SELF,

    /** 按租户隔离（默认）。 */
    TENANT
}
