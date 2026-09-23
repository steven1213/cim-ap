package com.cim.jpa.it;

import com.cim.spring.support.tenant.TenantContext;
import com.cim.spring.support.web.PageQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2.6 租户隔离验收（H2）：基于 {@code TenantContext} 的 Hibernate Filter 自动按租户追加
 * {@code tenant_id} 条件；业务经 {@code AbstractJpaService} 调用，零感知。超管（上下文为
 * {@code null}）跨租户可见。
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TenantIsolationTest {

    @Autowired
    private EquipmentDefService service;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void crossTenantInvisible() {
        // 以租户 T1 写入
        TenantContext.set("T1");
        EquipmentDef owned = service.save(newDef("EQ-T1", "光刻机", 10));
        assertThat(owned.getTenantId()).isEqualTo("T1");

        // 以租户 T2 查询：不可见 T1 的数据
        TenantContext.set("T2");
        assertThat(service.page(pageOf()).total()).isZero();

        // 切回 T1：可见
        TenantContext.set("T1");
        assertThat(service.load(owned.getId()).getId()).isEqualTo(owned.getId());

        // 超管（上下文 null）：跨租户可见
        TenantContext.clear();
        assertThat(service.page(pageOf()).total()).isGreaterThanOrEqualTo(1);
    }

    private EquipmentDef newDef(String code, String name, int capacity) {
        EquipmentDef d = new EquipmentDef();
        d.setCode(code);
        d.setName(name);
        d.setCapacity(capacity);
        return d;
    }

    private PageQuery pageOf() {
        PageQuery q = new PageQuery();
        q.setPage(1);
        q.setSize(10);
        return q;
    }
}
