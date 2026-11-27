package com.cim.mds.server.flow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 流程校验明细仓储（validate 一次全量替换：先按 flowId 删再插）。
 */
public interface CimFlowValidateResultRepository extends JpaRepository<CimFlowValidateResult, String> {

    List<CimFlowValidateResult> findByFlowIdAndDeletedFalseOrderBySeverityAsc(String flowId);

    List<CimFlowValidateResult> findByFlowIdAndDeletedFalse(String flowId);
}
