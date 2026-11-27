package com.cim.mds.server.edc;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EDC 设备接口映射仓储。
 */
public interface CimEdcEquipMapRepository extends JpaRepository<CimEdcEquipMap, String> {

    List<CimEdcEquipMap> findByDeletedFalseOrderByEquipmentCodeAscCeidAsc();

    List<CimEdcEquipMap> findByEquipmentCodeAndDeletedFalse(String equipmentCode);

    Optional<CimEdcEquipMap> findByEquipmentCodeAndCeidAndTplIdAndDeletedFalse(
            String equipmentCode, Integer ceid, String tplId);

    /** 模板被绑定引用守门（删除模板前调用）。 */
    boolean existsByTplIdAndDeletedFalse(String tplId);
}
