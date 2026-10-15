package com.cim.rms.server.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 机台 PPID ↔ 配方映射仓储（D7）。 */
public interface ToolRecipeMapRepository extends JpaRepository<ToolRecipeMap, String> {

    Optional<ToolRecipeMap> findFirstByDeviceIdAndPpidOnToolAndActiveTrueAndDeletedFalse(
            String deviceId, String ppidOnTool);

    Optional<ToolRecipeMap> findFirstByDeviceTypeIdAndPpidOnToolAndActiveTrueAndDeletedFalse(
            String deviceTypeId, String ppidOnTool);

    Optional<ToolRecipeMap> findFirstByDeviceIdAndPpidOnToolAndDeletedFalse(String deviceId, String ppidOnTool);

    Optional<ToolRecipeMap> findFirstByDeviceTypeIdAndPpidOnToolAndDeletedFalse(String deviceTypeId, String ppidOnTool);

    List<ToolRecipeMap> findByDeletedFalseOrderByCreateTimeDesc();

    /** 某机台的生效映射（机台配方清单核对时反查「RMS 有映射、机台未上报」= MISSING）。 */
    List<ToolRecipeMap> findByDeviceIdAndDeletedFalse(String deviceId);

    List<ToolRecipeMap> findByRecipeIdAndDeletedFalse(String recipeId);

    /** 腔体被引用校验（删腔体前的引用保护）。 */
    List<ToolRecipeMap> findByChamberIdAndDeletedFalse(String chamberId);

    /** 某机台有效映射（含腔体维度，供设备视角聚合）。 */
    List<ToolRecipeMap> findByDeviceIdAndActiveTrueAndDeletedFalse(String deviceId);
}
