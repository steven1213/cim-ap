package com.cim.jpa.tenant;

import com.cim.spring.support.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;

/**
 * 租户过滤器启用器（见 README §10）。
 *
 * <p>在每次数据访问前调用 {@link #apply(EntityManager)}，按 {@link TenantContext} 当前租户
 * 启用 Hibernate {@code cimTenantFilter} 并注入参数；业务代码经 {@code AbstractJpaService}
 * 调用，故零感知。{@code TenantContext} 为 {@code null}（超管跨租户）时不启用过滤。</p>
 *
 * <p>过滤器启用是基于「会话」的，同一事务内幂等；多次调用仅刷新参数。</p>
 *
 * <p><b>{@code @FilterDef} 的声明位置（ADR-12 —— 勿在实体上重复声明）</b>：Hibernate 要求
 * {@code @FilterDef} 的名字<b>在每个持久化单元内唯一</b>，同一个名字被声明两次会直接抛
 * {@code AnnotationException: Multiple '@FilterDef' annotations define a filter named 'cimTenantFilter'}
 * （Hibernate 6.2+ 起为硬错误，无开关可关，见 HHH-16581 / HHH-16803；「同名同参即幂等」是
 * 6.1 及更早的旧行为，<b>不成立</b>）。故平台的唯一声明点是
 * {@code com.cim.system}'s {@code package-info}；租户化实体只标
 * {@code @Filter(name="cimTenantFilter", condition="tenant_id = :tenantId")}（须<b>直接</b>标注，
 * Hibernate 不解析元注解，且显式 {@code condition} 使启动期不依赖 {@code @FilterDef} 是否在同一
 * 扫描包内）。</p>
 *
 * <p>本类以 {@code @Bean} 形式在 {@code JpaAutoConfiguration} 中声明（见该类的
 * {@code tenantFilterApplier()}），以便脱离包扫描也可注入。</p>
 */
public class TenantFilterApplier {

    /**
     * 在当前 Hibernate 会话上启用 / 刷新租户过滤器。
     *
     * @param entityManager 当前（事务绑定的）EntityManager
     */
    public void apply(EntityManager entityManager) {
        String tenant = TenantContext.get();
        if (tenant == null) {
            return; // 不启用隔离：超管跨租户（风险 R6）
        }
        Session session = entityManager.unwrap(Session.class);
        // enableFilter 对已启用的过滤器幂等返回，并刷新参数
        session.enableFilter("cimTenantFilter").setParameter("tenantId", tenant);
    }
}
