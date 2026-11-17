package com.cim.mds.server.reason;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * WIP 原因事件仓储（V16 示例域）。履历按发生时间倒序（新事件在前）。
 */
public interface CimWipReasonEventRepository extends JpaRepository<CimWipReasonEvent, String> {

    List<CimWipReasonEvent> findByDeletedFalseOrderByOccurredAtDescIdDesc();

    List<CimWipReasonEvent> findByLotIdAndDeletedFalseOrderByOccurredAtDescIdDesc(String lotId);

    /** 删码守门：被事件引用的码不允许删（历史可解析性）。 */
    boolean existsByReasonIdAndDeletedFalse(String reasonId);

    long countByDeletedFalse();
}
