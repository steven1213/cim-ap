package com.cim.jpa.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 端到端验收（H2）：主键注入 + 审计/租户填充 + 按 {@code @History} 自动落历史 +
 * 变更检测闸门（未变更不落历史）。
 */
@SpringBootTest(classes = TestApplication.class)
class HistoryIntegrationTest {

    @Autowired
    private EquipmentDefService service;

    @Autowired
    private EquipmentDefHistRepository histRepository;

    private EquipmentDef newDef(String code, String name, int capacity) {
        EquipmentDef def = new EquipmentDef();
        def.setCode(code);
        def.setName(name);
        def.setCapacity(capacity);
        return def;
    }

    private List<EquipmentDefHist> history(String bizId) {
        return histRepository.findByBizIdOrderByHistoryIdDesc(bizId);
    }

    @Test
    void saveInjectsIdFillsAuditAndWritesHistory() {
        EquipmentDef saved = service.save(newDef("EQ-001", "光刻机", 10));

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getEventTime()).isNotNull();
        assertThat(saved.getCreateUser()).isEqualTo("tester");
        assertThat(saved.getEventUser()).isEqualTo("tester");
        assertThat(saved.getTenantId()).isEqualTo("T1");
        assertThat(saved.getDeleted()).isFalse();

        List<EquipmentDefHist> hist = history(saved.getId());
        assertThat(hist).hasSize(1);
        EquipmentDefHist row = hist.get(0);
        assertThat(row.getOpType()).isEqualTo("I");
        assertThat(row.getBizId()).isEqualTo(saved.getId());
        assertThat(row.getCode()).isEqualTo("EQ-001");
        assertThat(row.getName()).isEqualTo("光刻机");
        assertThat(row.getOperator()).isEqualTo("tester");
        assertThat(row.getTenantId()).isEqualTo("T1");
        assertThat(row.getHistoryId()).isNotBlank();
        assertThat(row.getOpTime()).isNotNull();
    }

    @Test
    void updateWritesSnapshotWithChangeSet() {
        EquipmentDef saved = service.save(newDef("EQ-002", "刻蚀机", 5));

        saved.setName("刻蚀机-v2");
        saved.setCapacity(8);
        EquipmentDef updated = service.update(saved);

        List<EquipmentDefHist> hist = history(updated.getId());
        assertThat(hist).hasSize(2);

        EquipmentDefHist latest = hist.get(0);
        assertThat(latest.getOpType()).isEqualTo("U");
        assertThat(latest.getName()).isEqualTo("刻蚀机-v2");
        assertThat(latest.getCapacity()).isEqualTo(8);
        assertThat(latest.getChangeSetJson())
                .contains("name").contains("capacity").contains("刻蚀机-v2");
    }

    @Test
    void unchangedUpdateWritesNothing() {
        EquipmentDef saved = service.save(newDef("EQ-003", "涂胶机", 3));

        EquipmentDef loaded = service.load(saved.getId());
        service.update(loaded); // 字段无变化

        assertThat(history(saved.getId())).hasSize(1);
    }

    @Test
    void removeWritesDeleteHistory() {
        EquipmentDef saved = service.save(newDef("EQ-004", "显影机", 2));

        service.remove(saved.getId());

        List<EquipmentDefHist> hist = history(saved.getId());
        assertThat(hist).hasSize(2);
        assertThat(hist.get(0).getOpType()).isEqualTo("D");
        assertThat(hist.get(0).getCode()).isEqualTo("EQ-004");
    }
}
