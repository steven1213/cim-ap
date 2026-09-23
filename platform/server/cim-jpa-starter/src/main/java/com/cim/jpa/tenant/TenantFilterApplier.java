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
 * <p>租户化实体须**直接**标注（Hibernate 不解析元注解形式）：
 * {@code @FilterDef(name="cimTenantFilter", parameters=@ParamDef(name="tenantId", type=String.class))}
 * 与 {@code @Filter(name="cimTenantFilter", condition="tenant_id = :tenantId")}，同名 {@code FilterDef}
 * 在多实体上重复声明是幂等的。</p>
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
