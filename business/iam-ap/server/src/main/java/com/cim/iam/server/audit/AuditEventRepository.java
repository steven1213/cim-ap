package com.cim.iam.server.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {

    List<AuditEvent> findAllByOrderByIdDesc(Pageable pageable);

    List<AuditEvent> findByTypeOrderByIdDesc(String type, Pageable pageable);

    List<AuditEvent> findBySubjectOrderByIdDesc(String subject, Pageable pageable);

    long countByType(String type);
}
