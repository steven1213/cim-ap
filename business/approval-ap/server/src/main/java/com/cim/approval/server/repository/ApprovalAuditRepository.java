package com.cim.approval.server.repository;

import com.cim.approval.server.domain.ApprovalAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalAuditRepository extends JpaRepository<ApprovalAudit, String> {

    List<ApprovalAudit> findByBusinessKeyOrderBySeqAsc(String businessKey);

    List<ApprovalAudit> findByBusinessKeyAndAppCodeOrderBySeqAsc(String businessKey, String appCode);

    /**
     * 按域集合查询（G3）：认证态未指定 appCode 时收敛为「令牌域 ∪ 共享域」，
     * 用 IN 一次拿全，而不是先全量取回再内存过滤。
     */
    List<ApprovalAudit> findByBusinessKeyAndAppCodeInOrderBySeqAsc(String businessKey,
                                                                  java.util.Collection<String> appCodes);

    List<ApprovalAudit> findByAppCodeOrderBySeqAsc(String appCode);

    long countByBusinessKey(String businessKey);

    java.util.Optional<ApprovalAudit> findTopByBusinessKeyOrderBySeqDesc(String businessKey);

    boolean existsByProcessInstanceIdAndActor(String processInstanceId, String actor);

    long countByActionAndCreatedAtAfter(String action, java.time.Instant since);

    /** 按域统计动作次数（统计看板的驳回率，G3：不再跨域聚合）。 */
    long countByActionAndAppCodeAndCreatedAtAfter(String action, String appCode, java.time.Instant since);

    /** 撤回策略：实例已产生任一审批票（COMPLETE_*）则不可撤回。 */
    boolean existsByProcessInstanceIdAndActionStartingWith(String processInstanceId, String actionPrefix);
}
