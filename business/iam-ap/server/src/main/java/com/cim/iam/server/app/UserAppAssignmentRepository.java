package com.cim.iam.server.app;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAppAssignmentRepository extends JpaRepository<UserAppAssignment, String> {

    List<UserAppAssignment> findByUserIdAndStatus(String userId, AppStatus status);

    Optional<UserAppAssignment> findByUserIdAndAppCode(String userId, String appCode);

    List<UserAppAssignment> findByAppCode(String appCode);

    void deleteByUserIdAndAppCode(String userId, String appCode);
}
