package com.cim.iam.server.directory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 目录同步调度开关（identity-directory.md §6）。
 *
 * <p>仅在 {@code cim.iam.directory.ad.enabled=true} 时开启 Spring 调度——未配置 AD 时
 * 不注册任何定时任务，避免无谓的调度开销与测试干扰。</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "cim.iam.directory.ad", name = "enabled", havingValue = "true")
public class DirectorySyncConfig {
}
