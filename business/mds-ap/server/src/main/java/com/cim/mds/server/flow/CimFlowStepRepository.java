package com.cim.mds.server.flow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 流程步骤节点仓储（Flow 域）。 */
public interface CimFlowStepRepository extends JpaRepository<CimFlowStep, String> {

    /** 流程下的全部节点（step_no 升序 = 列表阅读序）。 */
    List<CimFlowStep> findByFlowIdAndDeletedFalseOrderByStepNoAsc(String flowId);

    Optional<CimFlowStep> findByIdAndFlowIdAndDeletedFalse(String id, String flowId);

    boolean existsByFlowIdAndStepCodeAndDeletedFalse(String flowId, String stepCode);

    /** 工序引用守门查据：是否有路线步骤引用该工序（工序库删/禁用守门用，V20）。 */
    boolean existsByOperationCodeAndDeletedFalse(String operationCode);
}
