package com.cim.jpa.it;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1.1 多库独立 EMF/TxManager/Hikari 验收：以第二个 H2 数据源验证「每库独立链路」，
 * 与 Spring Boot 主数据源互不干扰（离线以两个 H2 代三库；真实三库见 M7 / T7.2）。
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "cim.jpa.datasources.second.url=jdbc:h2:mem:cim_second;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "cim.jpa.datasources.second.username=sa",
        "cim.jpa.datasources.second.driver-class-name=org.h2.Driver",
        "cim.jpa.datasources.second.dialect=org.hibernate.dialect.H2Dialect",
        "cim.jpa.datasources.second.packages-to-scan=com.cim.jpa.it",
        "cim.jpa.datasources.second.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MultiDataSourceTest {

    @Autowired
    @Qualifier("secondEntityManagerFactory")
    private EntityManagerFactory secondEmf;

    @Autowired
    @Qualifier("secondTransactionManager")
    private PlatformTransactionManager secondTxManager;

    @Autowired
    private EntityManagerFactory primaryEmf;

    @Test
    void eachDatasourceHasIndependentEmfAndTxManager() {
        assertThat(secondEmf).isNotNull();
        assertThat(secondTxManager).isNotNull();
        assertThat(primaryEmf).isNotNull().isNotSameAs(secondEmf);
    }

    @Test
    void dataWrittenToSecondIsIsolatedFromPrimary() {
        // 写入第二个库
        EntityManager em = secondEmf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        EquipmentDef d = new EquipmentDef();
        d.setCode("DS2-1");
        d.setName("第二库设备");
        d.setCapacity(7);
        em.persist(d);
        tx.commit();
        em.close();

        // 第二个库可见
        EntityManager em2 = secondEmf.createEntityManager();
        Long inSecond = em2.createQuery("select count(e) from EquipmentDef e where e.code = :c", Long.class)
                .setParameter("c", "DS2-1").getSingleResult();
        em2.close();
        assertThat(inSecond).isEqualTo(1L);

        // 主库不可见（两库物理隔离）
        EntityManager pm = primaryEmf.createEntityManager();
        Long inPrimary = pm.createQuery("select count(e) from EquipmentDef e where e.code = :c", Long.class)
                .setParameter("c", "DS2-1").getSingleResult();
        pm.close();
        assertThat(inPrimary).isZero();
    }
}
