package com.cim.mds.server.flow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 流程独立规则仓储（V15）。规则随版本族锚定，评估按 (familyCode, versionNo) 取集合。
 */
public interface CimFlowRuleRepository extends JpaRepository<CimFlowRule, String> {

    List<CimFlowRule> findByFamilyCodeAndVersionNoAndDeletedFalseOrderByPriorityAscRuleCodeAsc(
            String familyCode, Integer versionNo);

    Optional<CimFlowRule> findByIdAndDeletedFalse(String id);

    boolean existsByFamilyCodeAndVersionNoAndRuleCodeAndDeletedFalse(
            String familyCode, Integer versionNo, String ruleCode);
}
