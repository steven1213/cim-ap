package com.cim.iam.server.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenVersionRepository extends JpaRepository<TokenVersion, String> {

    Optional<TokenVersion> findByUserId(String userId);
}
