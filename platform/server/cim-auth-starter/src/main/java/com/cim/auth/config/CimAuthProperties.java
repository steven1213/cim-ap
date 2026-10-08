package com.cim.auth.config;

import com.cim.auth.admission.AdmissionProperties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cim-auth-starter 配置键（前缀 {@code cim.auth.*}）。
 *
 * <p>对应 design.md §2.4 / §8：本 starter 只做「验签 + 准入 + 业务鉴权 + 数据权限」，
 * 登录/签发/吊销在 {@code business/iam-ap}。令牌公钥来自 IAM 的 JWKS 地址。</p>
 */
@ConfigurationProperties(prefix = "cim.auth")
public class CimAuthProperties {

    /** 总开关；关闭后整体回退到 cim-spring-support 的匿名操作人。 */
    private boolean enabled = true;

    /** IAM 发布的 JWKS 地址（含 RS256 公钥，按 kid 轮换）。 */
    private String jwksUri;

    /** JWKS 公钥缓存时长（分钟），到期重新拉取以支持密钥轮换。 */
    private long jwksCacheMinutes = 60;

    /** 准入配置（本 ap 的接入码等）。 */
    private AdmissionProperties admission = new AdmissionProperties();

    /** 业务权限集所在 claim（默认 {@code authorities}）。 */
    private String authoritiesClaim = "authorities";

    /** 令牌版本失效判定（对接 IAM 版本端点，§8.1(g)）。 */
    private TokenVersion tokenVersion = new TokenVersion();

    /** 令牌黑名单（主动吊销 / 登出，对接 IAM 黑名单端点，§8.1(h)）。 */
    private TokenBlacklist tokenBlacklist = new TokenBlacklist();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public long getJwksCacheMinutes() {
        return jwksCacheMinutes;
    }

    public void setJwksCacheMinutes(long jwksCacheMinutes) {
        this.jwksCacheMinutes = jwksCacheMinutes;
    }

    public AdmissionProperties getAdmission() {
        return admission;
    }

    public void setAdmission(AdmissionProperties admission) {
        this.admission = admission;
    }

    public String getAuthoritiesClaim() {
        return authoritiesClaim;
    }

    public void setAuthoritiesClaim(String authoritiesClaim) {
        this.authoritiesClaim = authoritiesClaim;
    }

    public TokenVersion getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(TokenVersion tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public TokenBlacklist getTokenBlacklist() {
        return tokenBlacklist;
    }

    public void setTokenBlacklist(TokenBlacklist tokenBlacklist) {
        this.tokenBlacklist = tokenBlacklist;
    }

    /**
     * 令牌版本失效判定配置（§8.1(g) 验证端）。
     *
     * <p>仅当 {@code iam-base-url} 配置时启用 {@link com.cim.auth.token.IamTokenVersionChecker}，
     * 否则保持默认 {@code acceptAll()}（不校验版本）。</p>
     */
    public static class TokenVersion {
        /** IAM 版本端点基址（如 {@code http://iam-ap:8081}）；为空则不启用版本校验。 */
        private String iamBaseUrl = "";

        /** IAM 版本查询端点路径（默认 {@code /api/v1/internal/token-version}）。 */
        private String endpoint = "/api/v1/internal/token-version";

        /** 本地版本缓存时长（分钟），到期重拉；默认 5。 */
        private long cacheMinutes = 5;

        public String getIamBaseUrl() {
            return iamBaseUrl;
        }

        public void setIamBaseUrl(String iamBaseUrl) {
            this.iamBaseUrl = iamBaseUrl;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public long getCacheMinutes() {
            return cacheMinutes;
        }

        public void setCacheMinutes(long cacheMinutes) {
            this.cacheMinutes = cacheMinutes;
        }
    }

    /**
     * 令牌黑名单判定配置（§8.1(h) 验证端）。
     *
     * <p>仅当 {@code iam-base-url} 配置时启用 {@link com.cim.auth.token.IamTokenBlacklistChecker}，
     * 否则保持默认 {@code acceptAll()}（不查黑名单）。</p>
     */
    public static class TokenBlacklist {
        /** IAM 黑名单端点基址（如 {@code http://iam-ap:8081}）；为空则不启用黑名单校验。 */
        private String iamBaseUrl = "";

        /** IAM 黑名单查询端点路径（默认 {@code /api/v1/internal/token-blacklist}）。 */
        private String endpoint = "/api/v1/internal/token-blacklist";

        /** 本地黑名单缓存时长（分钟），到期重拉；默认 5。 */
        private long cacheMinutes = 5;

        public String getIamBaseUrl() {
            return iamBaseUrl;
        }

        public void setIamBaseUrl(String iamBaseUrl) {
            this.iamBaseUrl = iamBaseUrl;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public long getCacheMinutes() {
            return cacheMinutes;
        }

        public void setCacheMinutes(long cacheMinutes) {
            this.cacheMinutes = cacheMinutes;
        }
    }
}
