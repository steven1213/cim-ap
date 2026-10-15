package com.cim.rms.server.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 操作审计日志仓储（要求 H2/MySQL 通用 JPQL，勿用方言函数）。 */
public interface RmsAuditLogRepository extends JpaRepository<RmsAuditLog, String> {

    /** 全量按时间倒序（最近在前）。 */
    List<RmsAuditLog> findByDeletedFalseOrderByCreateTimeDesc();

    /** 按动作类型过滤，时间倒序。 */
    List<RmsAuditLog> findByDeletedFalseAndTypeOrderByCreateTimeDesc(String type);
}
