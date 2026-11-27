package com.cim.mds.server.flow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 流程域事件历史仓库（V24，P3-2）——只读查询为主（append-only，无删除端点）。
 */
public interface FlowEventHistRepository extends JpaRepository<CimFlowEventHist, String> {

    /** 按流程取事件（时间倒序；调用侧限量，事件量有界）。 */
    List<CimFlowEventHist> findTop200ByFlowIdOrderByEventTimeDescIdDesc(String flowId);
}
