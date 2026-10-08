package com.cim.iam.server.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenBlacklistRepository extends JpaRepository<BlacklistedToken, String> {

    boolean existsByJti(String jti);

    Optional<BlacklistedToken> findByJti(String jti);
}
