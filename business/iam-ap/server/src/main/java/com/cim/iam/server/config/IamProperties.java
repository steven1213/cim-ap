package com.cim.iam.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IAM 签发配置（{@code cim.iam.jwt.*}）。
 *
 * <p>生产建议由 KMS / 配置中心注入 RSA 私钥（PEM），并支持按 {@code kid} 轮换；
 * 本地/演示未配置时 {@link com.cim.iam.server.token.RsaKeyService} 启动时生成临时密钥对
 * （重启即失效，仅用于联调）。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.jwt")
public class IamProperties {

    /** RSA 私钥 PEM（PKCS#8，含 -----BEGIN PRIVATE KEY-----）。为空则启动时生成临时密钥对。 */
    private String privateKeyPem = "";

    /** RSA 公钥 PEM（SPKI，含 -----BEGIN PUBLIC KEY-----）。与私钥配套；为空则随私钥一同生成。 */
    private String publicKeyPem = "";

    /** JWKS 中的密钥标识。 */
    private String kid = "iam-rsa-1";

    /** 令牌 issuer（sub 之外的标识，供验证端校验）。 */
    private String issuer = "cim-iam";

    /** 访问令牌有效期（分钟）。 */
    private long accessTokenTtlMinutes = 30;

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenTtlMinutes() {
        return accessTokenTtlMinutes;
    }

    public void setAccessTokenTtlMinutes(long accessTokenTtlMinutes) {
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
    }
}
