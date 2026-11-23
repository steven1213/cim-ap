package com.cim.iam.server.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 刷新令牌清理任务。
 *
 * <p>{@code revoked=true} 与已过期的刷新令牌行不再参与任何校验，但会一直留在表里——
 * H2 内存库重启即清无所谓，MySQL 持久库会无限膨胀（每次登录/轮转都产生行）。
 * 定期物理清理，间隔 {@code cim.iam.token.cleanup-interval}（ISO-8601 时长，默认 1 小时）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository repository;

    /** 首次延迟 2 分钟（避开启动高峰），之后按配置间隔清理。 */
    @Scheduled(initialDelayString = "PT2M",
            fixedDelayString = "${cim.iam.token.cleanup-interval:PT1H}")
    @Transactional
    public void purge() {
        long n = repository.deleteByRevokedTrueOrExpiresAtBefore(Instant.now());
        if (n > 0) {
            log.info("[token-cleanup] 清理刷新令牌 {} 行（已撤销/已过期）", n);
        }
    }
}
