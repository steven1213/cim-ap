package com.cim.iam.server.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, String> {

    Optional<LocalCredential> findByUsername(String username);

    Optional<LocalCredential> findByUserId(String userId);
}
