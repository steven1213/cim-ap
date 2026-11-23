package com.cim.iam.server.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 刷新令牌清理调度开关。
 *
 * <p>调度总开关默认开启（{@code cim.iam.token.cleanup-enabled=false} 可关）。
 * 注意不能复用 {@code DirectorySyncConfig} 的 {@code @EnableScheduling}——
 * 那里以 AD 目录启用为条件，未接 AD 的环境调度根本不会注册。</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "cim.iam.token", name = "cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
public class TokenCleanupConfig {
}
