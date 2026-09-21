package com.cim.jpa.it;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 历史表仓储（测试断言用）。
 */
public interface EquipmentDefHistRepository extends JpaRepository<EquipmentDefHist, String> {

    /**
     * 按业务主键查询历史，按 {@code historyId} 倒序（应用层时间有序 ID，同毫秒下仍确定）。
     */
    List<EquipmentDefHist> findByBizIdOrderByHistoryIdDesc(String bizId);
}
