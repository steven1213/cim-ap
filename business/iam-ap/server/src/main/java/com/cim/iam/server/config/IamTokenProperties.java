package com.cim.iam.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IAM 令牌相关配置（前缀 {@code cim.iam.token}）。
 *
 * <p>刷新令牌有效期等签发侧参数，与 {@code cim.iam.jwt}（访问令牌参数）并列。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.token")
public class IamTokenProperties {

    /** 刷新令牌有效期（分钟），默认 7 天（10080）。 */
    private long refreshTokenTtlMinutes = 10080;

    public long getRefreshTokenTtlMinutes() {
        return refreshTokenTtlMinutes;
    }

    public void setRefreshTokenTtlMinutes(long refreshTokenTtlMinutes) {
        this.refreshTokenTtlMinutes = refreshTokenTtlMinutes;
    }
}
