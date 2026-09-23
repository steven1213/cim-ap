package com.cim.jpa.it;

import com.cim.spring.support.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T2.5 软删唯一约束验收（H2）：唯一约束含 {@code deleted} 列，保证「一删一活可并存」
 * （见 README §9.7 / design.md §2.3）。逻辑删除经 {@code @Where(deleted=false)} 自动排除。
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SoftDeleteUniqueTest {

    @Autowired
    private EquipmentDefService service;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void oneDeletedOneActiveCanCoexist() {
        // 写入有效行（tenant_id 由 TenantPort 填充 T1）
        EquipmentDef active = service.save(newDef("EQ-001", "A", 1));
        assertThat(active.getTenantId()).isEqualTo("T1");
        assertThat(active.getDeleted()).isFalse();

        // 逻辑删除
        service.remove(active.getId());

        // 同 (code, tenant_id) 再写入一条有效行 → 允许（已删行 + 有效行并存）
        EquipmentDef newActive = service.save(newDef("EQ-001", "B", 2));
        assertThat(newActive.getDeleted()).isFalse();

        // 业务查询（@Where 自动排除已删）只返回有效行
        assertThat(service.page(pageOf()).total()).isEqualTo(1);

        // 物理行数 = 2（已删 + 有效），证明「一删一活」并存
        assertThat(physicalCount()).isEqualTo(2);

        // 再插入第二条有效行 → 唯一约束 (code, tenant_id, deleted=false) 冲突
        assertThatThrownBy(() -> service.save(newDef("EQ-001", "C", 3)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long physicalCount() {
        return ((Number) entityManager.createNativeQuery("select count(*) from equipment_def")
                .getSingleResult()).longValue();
    }

    private EquipmentDef newDef(String code, String name, int capacity) {
        EquipmentDef d = new EquipmentDef();
        d.setCode(code);
        d.setName(name);
        d.setCapacity(capacity);
        return d;
    }

    private com.cim.spring.support.web.PageQuery pageOf() {
        com.cim.spring.support.web.PageQuery q = new com.cim.spring.support.web.PageQuery();
        q.setPage(1);
        q.setSize(10);
        return q;
    }
}
