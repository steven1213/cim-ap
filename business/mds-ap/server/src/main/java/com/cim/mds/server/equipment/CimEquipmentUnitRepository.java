package com.cim.mds.server.equipment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 设备子单元仓储。
 *
 * <p>级联删除用 {@code @Modifying @Query} 物理删除（立即执行 SQL）：软删会撞
 * (equipment_id, unit_code, deleted) 唯一索引——Hibernate 同事务先 flush insert
 * 后执行 update/delete 的顺序问题。</p>
 */
public interface CimEquipmentUnitRepository extends JpaRepository<CimEquipmentUnit, String> {

    List<CimEquipmentUnit> findByEquipmentIdAndDeletedFalseOrderByUnitCodeAsc(String equipmentId);

    boolean existsByEquipmentIdAndUnitCodeAndDeletedFalse(String equipmentId, String unitCode);

    @Modifying
    @Query("delete from CimEquipmentUnit u where u.equipmentId = :equipmentId")
    void deleteByEquipmentId(@Param("equipmentId") String equipmentId);

    /** 物理行计数（测试级联删除断言用，含软删行）。 */
    long countByEquipmentId(String equipmentId);
}
