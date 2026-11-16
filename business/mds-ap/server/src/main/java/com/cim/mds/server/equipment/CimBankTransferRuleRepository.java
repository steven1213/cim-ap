package com.cim.mds.server.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Bank 间转运规则仓储。 */
public interface CimBankTransferRuleRepository extends JpaRepository<CimBankTransferRule, String> {

    Optional<CimBankTransferRule> findByFromBankIdAndToBankIdAndDeletedFalse(String fromBankId, String toBankId);

    boolean existsByFromBankIdAndDeletedFalse(String fromBankId);

    boolean existsByToBankIdAndDeletedFalse(String toBankId);

    /** 与指定 Bank 相关的全部规则（作为 from 或 to；优先级降序，空值按 0）。 */
    @Query("""
            select r from CimBankTransferRule r
             where r.deleted = false
               and (r.fromBankId = :bankId or r.toBankId = :bankId)
             order by coalesce(r.priority, 0) desc
            """)
    List<CimBankTransferRule> findByBank(@Param("bankId") String bankId);

    /** 全量规则列表（规则总览用）。 */
    @Query("""
            select r from CimBankTransferRule r
             where r.deleted = false
             order by coalesce(r.priority, 0) desc
            """)
    List<CimBankTransferRule> searchAll();
}
