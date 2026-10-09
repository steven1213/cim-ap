package com.cim.iam.server.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrgAppAssignmentRepository extends JpaRepository<OrgAppAssignment, String> {

    List<OrgAppAssignment> findByOrgId(String orgId);

    List<OrgAppAssignment> findByAppCode(String appCode);

    List<OrgAppAssignment> findByStatus(OrgStatus status);

    Optional<OrgAppAssignment> findByOrgIdAndAppCode(String orgId, String appCode);

    boolean existsByOrgId(String orgId);

    void deleteByOrgId(String orgId);
}
