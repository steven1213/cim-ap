package com.cim.approval.server.repository;

import com.cim.approval.server.domain.CallbackRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CallbackRegistrationRepository extends JpaRepository<CallbackRegistration, String> {
    Optional<CallbackRegistration> findByAppCode(String appCode);
}
