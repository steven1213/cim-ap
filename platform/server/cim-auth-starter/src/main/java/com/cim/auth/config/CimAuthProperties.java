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
}
