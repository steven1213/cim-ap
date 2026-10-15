package com.cim.rms.server.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 机台配方清单仓储（{@code (deviceId, ppidOnTool)} 唯一，上报即刷新快照）。 */
public interface ToolRecipeInventoryRepository extends JpaRepository<ToolRecipeInventory, String> {

    Optional<ToolRecipeInventory> findFirstByDeviceIdAndPpidOnToolAndDeletedFalse(String deviceId,
                                                                                  String ppidOnTool);

    List<ToolRecipeInventory> findByDeviceIdAndDeletedFalseOrderByPpidOnToolAsc(String deviceId);

    List<ToolRecipeInventory> findByDeletedFalseOrderByCreateTimeDesc();

    List<ToolRecipeInventory> findByReconcileResultAndDeletedFalseOrderByCreateTimeDesc(
            ReconcileResult reconcileResult);
}
