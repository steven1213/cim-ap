package com.cim.mds.server.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 设备自定义属性仓库（一期骨架，API 二期开放）。 */
public interface CimEquipmentAttrRepository extends JpaRepository<CimEquipmentAttr, String> {

    List<CimEquipmentAttr> findByEquipmentIdAndDeletedFalse(String equipmentId);
}
