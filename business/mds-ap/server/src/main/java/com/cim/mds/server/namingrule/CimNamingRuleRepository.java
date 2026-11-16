package com.cim.mds.server.namingrule;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * 编码规则仓储。
 *
 * <p>活跃规则查询是 create 守门的热路径：按 object_type 找 enabled 行
 * （active_marker='ENABLED' 保证至多一条）。</p>
 */
public interface CimNamingRuleRepository extends JpaRepository<CimNamingRule, String> {

    Optional<CimNamingRule> findByIdAndDeletedFalse(String id);

    Optional<CimNamingRule> findByObjectTypeAndEnabledTrueAndDeletedFalse(String objectType);

    /** 活跃规则（占号路径加行锁，防并发双号——实际上唯一索引+计数器行锁已挡，此处锁规则行保读一致）。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CimNamingRule r where r.objectType = :objectType " +
            "and r.enabled = true and r.deleted = false")
    Optional<CimNamingRule> lockActiveByObjectType(@Param("objectType") String objectType);
}
