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
}
