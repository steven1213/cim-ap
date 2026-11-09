package com.cim.notify.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * 实时推送配置（{@code cim.notify.*}）。
 *
 * <p>时间类配置统一用 Spring 的 {@link Duration} 写法（{@code 25s} / {@code 5m}）；
 * 因 {@link DurationUnit} 标注，裸数字仍按秒解释，与旧配置兼容。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cim.notify")
public class NotifyProperties {

    /** 总开关（默认开）。 */
    private boolean enabled = true;

    /**
     * 宿主 ap 的 app-code，用于 Redis 频道命名空间 {@code cim:notify:{appCode}}（必填）。
     * starter 无法自探，须由各 ap 在 application.yml 显式声明（如 rms-ap / approval-ap）。
     */
    private String appCode;

    /** 跨实例扇出（默认开 → RedisSseFanout；关闭 → LocalSseFanout 单实例内存）。 */
    private final Cluster cluster = new Cluster();

    /** 心跳间隔（默认 25s，须小于反向代理 proxy_read_timeout）。 */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration heartbeat = Duration.ofSeconds(25);

    /** 单连接超时（默认 5m）；超时后由客户端指数退避重连。勿设极大值永久挂连。 */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration timeout = Duration.ofSeconds(300);

    /** Redis 频道前缀；实际频道 = {@code channelPrefix:appCode}。 */
    private String channelPrefix = "cim:notify";

    /** 单用户并发连接上限（防滥用）。 */
    private int maxConnectionsPerUser = 5;

    /** dev 模式允许 {@code ?userId=&tenantId=} 直连建连（仅 dev profile 开启）。 */
    private boolean devAcceptQueryIdentity = false;

    @Getter
    @Setter
    public static class Cluster {
        private boolean enabled = true;
    }
}
