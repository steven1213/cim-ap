package com.cim.mds.server.flow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** 流程连接边仓储（Flow 域）。 */
public interface CimFlowEdgeRepository extends JpaRepository<CimFlowEdge, String> {

    /** 流程下的全部边。 */
    List<CimFlowEdge> findByFlowIdAndDeletedFalse(String flowId);

    /** 某流程里某节点的全部出边（选路/守门用）。 */
    List<CimFlowEdge> findByFlowIdAndFromStepIdAndDeletedFalse(String flowId, String fromStepId);
}
