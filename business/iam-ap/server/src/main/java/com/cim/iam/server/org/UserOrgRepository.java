package com.cim.iam.server.org;

import com.cim.iam.server.common.DataOrigin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserOrgRepository extends JpaRepository<UserOrg, String> {

    List<UserOrg> findByUserId(String userId);

    Optional<UserOrg> findByUserIdAndOrgId(String userId, String orgId);

    List<UserOrg> findByOrgId(String orgId);

    List<UserOrg> findByOrgIdIn(List<String> orgIds);

    List<UserOrg> findByUserIdAndSource(String userId, DataOrigin source);

    void deleteByUserId(String userId);
}
