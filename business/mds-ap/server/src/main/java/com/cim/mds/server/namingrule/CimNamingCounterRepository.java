package com.cim.mds.server.namingrule;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * 编码流水号计数器仓储（段级：同一规则每个 SEQ 段独立计数）。
 *
 * <p>占号走 {@code FOR UPDATE}（{@link #lockForAllocate}）：同事务内锁桶行 →
 * 递增 → 拼码；并发 create 排队串行化，号不重不跳（回滚的号随事务丢弃，
 * 产生空洞可接受——号段连续性不是契约，唯一性才是）。</p>
 */
public interface CimNamingCounterRepository extends JpaRepository<CimNamingCounter, String> {

    List<CimNamingCounter> findByRuleIdAndDeletedFalse(String ruleId);

    Optional<CimNamingCounter> findByRuleIdAndSegmentIndexAndScopeKeyAndDeletedFalse(
            String ruleId, Integer segmentIndex, String scopeKey);

    /** 占号专用：锁桶行（不存在由服务层插入首行）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CimNamingCounter c where c.ruleId = :ruleId " +
            "and c.segmentIndex = :segmentIndex and c.scopeKey = :scopeKey and c.deleted = false")
    Optional<CimNamingCounter> lockForAllocate(@Param("ruleId") String ruleId,
                                               @Param("segmentIndex") Integer segmentIndex,
                                               @Param("scopeKey") String scopeKey);
}
