package com.cim.rms.server.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 接口幂等去重仓储（Req §5.2 IF-COM）。 */
public interface IntegrationIdempotencyRepository extends JpaRepository<IntegrationIdempotency, String> {

    /** 命中首次执行的记录（重试时回放结果）。 */
    Optional<IntegrationIdempotency> findFirstByScopeAndIdemKeyAndDeletedFalse(String scope, String idemKey);

    /** 运维排查用：某接口的去重记录（时间倒序）。 */
    List<IntegrationIdempotency> findByScopeAndDeletedFalseOrderByCreateTimeDesc(String scope);
}
