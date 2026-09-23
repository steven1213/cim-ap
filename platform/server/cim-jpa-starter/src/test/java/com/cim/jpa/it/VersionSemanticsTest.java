package com.cim.jpa.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T2.7 三层版本语义验收（H2）：{@code @Version}（行级乐观锁）/ {@code revision}（业务版本）/
 * 历史表，三者是**相互独立**的机制，各自按自己的语义演进。
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class VersionSemanticsTest {

    @Autowired
    private EquipmentDefService defService;

    @Autowired
    private DocRevisionRepository docRevisionRepository;

    @Autowired
    private EquipmentDefHistRepository histRepository;

    /** ① @Version 行级乐观锁：每次写自动 +1；③ 历史表独立累加。 */
    @Test
    void versionAndHistoryEvolveIndependently() {
        EquipmentDef saved = defService.save(newDef("DOC-1", "v1", 1));
        long v0 = saved.getVersion(); // 插入后初始版本（Hibernate 置 0）
        assertThat(v0).isNotNull();

        EquipmentDef u1 = defService.update(withName(saved, "v2"));
        assertThat(u1.getVersion()).isEqualTo(v0 + 1);

        EquipmentDef u2 = defService.update(withName(u1, "v3"));
        assertThat(u2.getVersion()).isEqualTo(v0 + 2);

        // 历史表独立记录 I / U / U，与主表 version 无关
        assertThat(histRepository.findByBizIdOrderByHistoryIdDesc(saved.getId()))
                .hasSize(3)
                .extracting(EquipmentDefHist::getOpType)
                .containsExactly("U", "U", "I");
    }

    /** ② revision 业务版本：由业务动作驱动，可任意设值；与 @Version 互不干扰。 */
    @Test
    void revisionIsBusinessDrivenAndIndependent() {
        DocRevision doc = new DocRevision();
        doc.setTitle("草稿");
        doc.setRevision(1);
        DocRevision saved = docRevisionRepository.save(doc);
        assertThat(saved.getRevision()).isEqualTo(1);

        saved.setTitle("发布 v2");
        saved.setRevision(2); // 业务版本按发布动作 +1
        DocRevision updated = docRevisionRepository.save(saved);
        assertThat(updated.getRevision()).isEqualTo(2);
        // BaseRevisionData 无 @Version，revision 即其版本语义，与乐观锁列无关
        assertThat(updated).extracting(DocRevision::getRevision).isNotEqualTo(0);
    }

    private EquipmentDef newDef(String code, String name, int capacity) {
        EquipmentDef d = new EquipmentDef();
        d.setCode(code);
        d.setName(name);
        d.setCapacity(capacity);
        return d;
    }

    private EquipmentDef withName(EquipmentDef src, String name) {
        EquipmentDef d = new EquipmentDef();
        d.setId(src.getId());
        d.setCode(src.getCode());
        d.setName(name);
        d.setCapacity(src.getCapacity());
        d.setVersion(src.getVersion());
        return d;
    }
}
