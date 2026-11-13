package com.cim.mds.server.site;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * 位置主数据编辑锁配置（{@code cim.mds.location.*}）。
 *
 * <p>时间一律 {@link Duration} + {@code @DurationUnit}（平台约定）：
 * 配 {@code 30} 按 {@link ChronoUnit#MINUTES 分钟} 解释，也接受 {@code PT30M} / {@code 30m}。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cim.mds.location")
public class EditLockProperties {

    /**
     * 编辑锁超时：{@code occupied_at} 早于 now - timeout 视为残留锁，他人可抢锁覆盖。
     * 默认 30 分钟。
     */
    @DurationUnit(ChronoUnit.MINUTES)
    private Duration editLockTimeout = Duration.ofMinutes(30);
}
