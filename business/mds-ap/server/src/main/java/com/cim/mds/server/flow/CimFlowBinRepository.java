package com.cim.mds.server.flow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 流程决策 bin 词表仓储（解析产物；saveXml 全量替换）。
 */
public interface CimFlowBinRepository extends JpaRepository<CimFlowBin, String> {

    List<CimFlowBin> findByFlowIdAndDeletedFalse(String flowId);
}
