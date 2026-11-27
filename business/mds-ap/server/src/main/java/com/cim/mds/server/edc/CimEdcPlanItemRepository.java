package com.cim.mds.server.edc;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EDC 方案明细仓储。
 */
public interface CimEdcPlanItemRepository extends JpaRepository<CimEdcPlanItem, String> {

    List<CimEdcPlanItem> findByPlanIdAndDeletedFalseOrderBySortNoAsc(String planId);

    boolean existsByPlanIdAndDeletedFalse(String planId);

    /** 删除方案时明细物理级联（软删会撞 uk_edc_plan_item）。 */
    void deleteByPlanId(String planId);
}
