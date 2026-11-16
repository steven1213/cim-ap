package com.cim.mds.server.flow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 跨站 Q-time 约束仓储（解析产物；saveXml 全量替换）。
 */
public interface CimFlowQtimeRepository extends JpaRepository<CimFlowQtime, String> {

    List<CimFlowQtime> findByFlowIdAndDeletedFalse(String flowId);
}
