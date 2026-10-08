package com.cim.iam.server.app;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppRegistrationRepository extends JpaRepository<AppRegistration, String> {

    Optional<AppRegistration> findByAppCode(String appCode);

    List<AppRegistration> findByStatus(AppStatus status);
}
