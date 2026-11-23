package com.cim.iam.server.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 未撤销且未过期的刷新令牌（在线会话列表用）。 */
    List<RefreshToken> findByRevokedFalseAndExpiresAtAfterOrderByExpiresAtAsc(Instant now);

    /** 某用户全部活跃刷新令牌（单会话挤下线 / 强制下线时撤销用）。 */
    List<RefreshToken> findByUserIdAndRevokedFalseAndExpiresAtAfter(String userId, Instant now);

    /** 清理已撤销或已过期的刷新令牌行（定时任务用，防持久库膨胀）。 */
    long deleteByRevokedTrueOrExpiresAtBefore(Instant now);
}
