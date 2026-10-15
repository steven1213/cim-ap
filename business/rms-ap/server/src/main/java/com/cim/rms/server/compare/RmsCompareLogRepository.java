package com.cim.rms.server.compare;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 比对校验日志仓储。 */
public interface RmsCompareLogRepository extends JpaRepository<RmsCompareLog, String> {

    /** 全量按时间倒序。 */
    List<RmsCompareLog> findByDeletedFalseOrderByCreateTimeDesc();

    /** 按结果过滤（PASS / FAIL），时间倒序。 */
    List<RmsCompareLog> findByDeletedFalseAndResultOrderByCreateTimeDesc(String result);
}
