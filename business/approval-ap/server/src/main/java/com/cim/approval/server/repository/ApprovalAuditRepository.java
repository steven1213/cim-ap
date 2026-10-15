package com.cim.approval.server.repository;

import com.cim.approval.server.domain.ApprovalAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalAuditRepository extends JpaRepository<ApprovalAudit, String> {

    List<ApprovalAudit> findByBusinessKeyOrderBySeqAsc(String businessKey);

    long countByBusinessKey(String businessKey);

    java.util.Optional<ApprovalAudit> findTopByBusinessKeyOrderBySeqDesc(String businessKey);

    boolean existsByProcessInstanceIdAndActor(String processInstanceId, String actor);

    long countByActionAndCreatedAtAfter(String action, java.time.Instant since);

    /** 撤回策略：实例已产生任一审批票（COMPLETE_*）则不可撤回。 */
    boolean existsByProcessInstanceIdAndActionStartingWith(String processInstanceId, String actionPrefix);
}
