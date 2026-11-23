package com.cim.iam.server.token;

import com.cim.core.port.IdGenerator;
import com.cim.iam.server.auth.RefreshToken;
import com.cim.iam.server.auth.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 令牌版本存储与递增（§8.1(g) 存储/递增端）。
 *
 * <p>版本为「按用户的单调递增计数」。{@link #currentVersion(String)} 在首次访问时为该用户建记录（版本 1）；
 * {@link #bump(String)} 使版本 +1，用于主动作废该用户所有已签发令牌。</p>
 *
 * <p><b>bump 的完整语义 = 「该用户全部令牌失效」</b>：除版本 +1（旧访问令牌在验证端即时 401）外，
 * 还<b>撤销该用户全部活跃刷新令牌</b>。只 bump 不撤销刷新令牌是历史缺口——被下线用户的客户端
 * 自动刷新时，rotate 会按<b>当前</b>版本重签访问令牌，等于「强制下线一个刷新周期后自动复活」。
 * 撤销后刷新直接 401，下线才真正落地。调用方（登录挤下线 / 强制下线 / 禁用 / 改密 / 准入变更）
 * 语义一致，无需各自处理。SSO 静默续发（{@code /login} 带 SSO Cookie）不走 bump，多 ap 复用不受影响。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenVersionService {

    private final TokenVersionRepository repository;
    private final RefreshTokenRepository refreshTokens;
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

    /**
     * 版本 +1 并撤销该用户全部活跃刷新令牌，返回新版本。
     * 用于登录挤下线（单会话）/ 强制下线 / 禁用 / 改密 / 准入与档案变更。
     */
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

        // 撤销全部活跃刷新令牌：不撤销则被下线端 rotate 后按新 ver 复活（见类注释）
        Instant now = Instant.now();
        List<RefreshToken> active = refreshTokens.findByUserIdAndRevokedFalseAndExpiresAtAfter(userId, now);
        if (!active.isEmpty()) {
            for (RefreshToken rt : active) {
                rt.setRevoked(true);
                rt.setRevokedAt(now);
            }
            refreshTokens.saveAll(active);
        }
        log.info("[token-version] bump user={} -> version={} revokedSessions={}",
                userId, tv.getTokenVersion(), active.size());
        return tv.getTokenVersion();
    }
}
