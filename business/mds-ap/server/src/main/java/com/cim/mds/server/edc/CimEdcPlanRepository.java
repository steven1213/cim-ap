package com.cim.mds.server.edc;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EDC 方案仓储。
 */
public interface CimEdcPlanRepository extends JpaRepository<CimEdcPlan, String> {

    List<CimEdcPlan> findByDeletedFalseOrderByPlanCodeAscVersionNoDesc();

    List<CimEdcPlan> findByPlanCodeAndDeletedFalseOrderByVersionNoDesc(String planCode);

    Optional<CimEdcPlan> findByPlanCodeAndVersionNoAndDeletedFalse(String planCode, Integer versionNo);

    /** 同族现行 ACTIVE（activate 自动降级目标）。 */
    Optional<CimEdcPlan> findByPlanCodeAndDeletedFalseAndStatus(String planCode, String status);

    int countByPlanCodeAndDeletedFalse(String planCode);

    /** 参数集删除守门：被方案引用。 */
    boolean existsBySetCodeAndDeletedFalse(String setCode);
}
