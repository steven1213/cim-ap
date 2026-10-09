package com.cim.iam.server.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 未撤销且未过期的刷新令牌（在线会话列表用）。 */
    List<RefreshToken> findByRevokedFalseAndExpiresAtAfterOrderByExpiresAtAsc(Instant now);
}
