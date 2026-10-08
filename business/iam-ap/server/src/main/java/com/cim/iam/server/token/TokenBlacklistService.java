package com.cim.iam.server.token;

import com.cim.core.port.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 令牌黑名单存储与查询（§8.1(h) 存储端）。
 *
 * <p>{@link #revoke(String, String, Instant)} 幂等：同一 jti 重复吊销不报错。
 * {@link #isRevoked(String)} 供内部端点暴露给验证侧查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final TokenBlacklistRepository repository;
    private final IdGenerator idGenerator;

    /** 拉黑某 jti（幂等）。 */
    @Transactional
    public void revoke(String jti, String userId, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        if (repository.existsByJti(jti)) {
            return; // 已拉黑，幂等
        }
        BlacklistedToken e = new BlacklistedToken();
        e.setId(idGenerator.nextId());
        e.setJti(jti);
        e.setUserId(userId);
        e.setExpiresAt(expiresAt);
        e.setRevokedAt(Instant.now());
        repository.save(e);
        log.info("[token-blacklist] 拉黑 jti={} user={}", jti, userId);
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return repository.existsByJti(jti);
    }
}
