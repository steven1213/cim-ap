package com.cim.iam.server.token;

import com.cim.core.port.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 令牌版本存储与递增（§8.1(g) 存储/递增端）。
 *
 * <p>版本为「按用户的单调递增计数」。{@link #currentVersion(String)} 在首次访问时为该用户建记录（版本 1）；
 * {@link #bump(String)} 使版本 +1，用于主动作废该用户所有已签发令牌。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenVersionService {

    private final TokenVersionRepository repository;
    private final IdGenerator idGenerator;

    /** 当前版本（首次访问建记录并返回 1）。 */
    @Transactional
    public long currentVersion(String userId) {
        return repository.findByUserId(userId)
                .map(TokenVersion::getTokenVersion)
                .orElseGet(() -> {
                    TokenVersion tv = new TokenVersion();
                    tv.setId(idGenerator.nextId());
                    tv.setUserId(userId);
                    tv.setTokenVersion(1L);
                    tv.setBumpedAt(Instant.now());
                    repository.save(tv);
                    return 1L;
                });
    }

    /** 版本 +1，返回新版本；用于改权限 / 改密 / 强制下线。 */
    @Transactional
    public long bump(String userId) {
        TokenVersion tv = repository.findByUserId(userId).orElseGet(() -> {
            TokenVersion t = new TokenVersion();
            t.setId(idGenerator.nextId());
            t.setUserId(userId);
            t.setTokenVersion(0L);
            return t;
        });
        tv.setTokenVersion(tv.getTokenVersion() + 1);
        tv.setBumpedAt(Instant.now());
        repository.save(tv);
        log.info("[token-version] bump user={} -> version={}", userId, tv.getTokenVersion());
        return tv.getTokenVersion();
    }
}
