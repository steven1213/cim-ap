package com.cim.auth.datapermission;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * 数据权限谓词构造器（design.md §2.4 / T3.6）。
 *
 * <p>Repository 构建查询时调用 {@code DataPermissionSpec.toPredicate(root, cb)} 叠加到原查询，
 * 依据 {@link DataPermissionContextHolder} 中的当前作用域生成过滤谓词：</p>
 * <ul>
 *   <li>无上下文 / 超管 → 不过滤（conjunction）；</li>
 *   <li>TENANT → {@code tenant_id = ?}；</li>
 *   <li>SELF → {@code create_user = ?}；</li>
 *   <li>DEPT / FACTORY → 对应字段相等。</li>
 * </ul>
 *
 * <p>纯 JPA Criteria，无字符串拼接，避免注入。仅依赖 {@code jakarta.persistence.criteria}，
 * 使本 starter 保持 JPA 无关；消费者（如 cim-system）用一行即可包成 spring-data 的
 * {@code Specification}：{@code (root, q, cb) -> DataPermissionSpec.toPredicate(root, cb)}。</p>
 *
 * @param <T> 实体类型
 */
public final class DataPermissionSpec {

    private DataPermissionSpec() {
    }

    public static <T> Predicate toPredicate(Root<T> root, CriteriaBuilder cb) {
        DataPermissionContext ctx = DataPermissionContextHolder.get();
        if (ctx == null || ctx.isSuper) {
            return cb.conjunction();
        }
        return switch (ctx.scope) {
            case TENANT -> cb.equal(root.get("tenantId"), ctx.tenantId);
            case SELF -> cb.equal(root.get("createUser"), ctx.userId);
            case DEPT -> cb.equal(root.get("deptId"), ctx.deptId);
            case FACTORY -> cb.equal(root.get("factoryId"), ctx.factoryId);
        };
    }
}
