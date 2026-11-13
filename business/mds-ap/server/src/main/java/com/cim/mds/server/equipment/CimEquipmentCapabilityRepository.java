package com.cim.mds.server.equipment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 设备↔能力关联仓储。 */
public interface CimEquipmentCapabilityRepository extends JpaRepository<CimEquipmentCapability, String> {

    List<CimEquipmentCapability> findByEquipmentIdAndDeletedFalse(String equipmentId);

    List<CimEquipmentCapability> findByCapabilityCodeAndDeletedFalse(String capabilityCode);

    /**
     * 物理批量删（设备能力全量替换用）。必须是 {@code @Modifying} bulk delete：
     * 派生 deleteBy 会走实体删除，Hibernate flush 时 <b>delete 排在 insert 之后</b>，
     * 「删旧+插新」同事务会撞 uk_cim_eqp_cap。
     */
    @Modifying
    @Query("delete from CimEquipmentCapability c where c.equipmentId = :equipmentId")
    void deleteByEquipmentId(@Param("equipmentId") String equipmentId);
}
