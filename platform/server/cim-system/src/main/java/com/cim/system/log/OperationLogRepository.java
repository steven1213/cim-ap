package com.cim.system.log;

import com.cim.jpa.support.BaseRepository;

import java.util.List;

/** 操作日志仓储（流水表，追加为主，查询用 Specification 走 {@code BaseRepository}）。 */
public interface OperationLogRepository extends BaseRepository<OperationLog, String> {

    /** 某业务键的操作轨迹（按时间倒序）。 */
    List<OperationLog> findByBizKeyOrderByEventTimeDesc(String bizKey);
}
