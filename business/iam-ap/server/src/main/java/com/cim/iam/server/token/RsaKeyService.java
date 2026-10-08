package com.cim.iam.server.token;

import com.cim.iam.server.config.IamProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * IAM RSA 密钥管理（RS256 令牌签发 / JWKS 发布）。
 *
 * <p>优先使用配置注入的 PEM（生产由 KMS 提供）；未配置则在启动时生成临时 RSA-2048 密钥对，
 * 仅用于本地/演示联调（重启失效）。公钥经 {@link JwksController} 以 JWKS 形态发布。</p>
 */
@Slf4j
@Getter
@Component
public class RsaKeyService {

    private final IamProperties properties;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public RsaKeyService(IamProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        String privPem = properties.getPrivateKeyPem();
        String pubPem = properties.getPublicKeyPem();
        if (privPem != null && !privPem.isBlank() && pubPem != null && !pubPem.isBlank()) {
            try {
                this.privateKey = parsePrivateKey(privPem);
                this.publicKey = parsePublicKey(pubPem);
                log.info("[iam-rsa] 使用配置注入的 RSA 密钥（kid={}）", properties.getKid());
                return;
            } catch (Exception e) {
                log.warn("[iam-rsa] 配置密钥解析失败，回退生成临时密钥对：{}", e.getMessage());
            }
        }
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            this.privateKey = kp.getPrivate();
            this.publicKey = kp.getPublic();
            log.warn("[iam-rsa] 已生成临时 RSA-2048 密钥对（kid={}）；生产请由 KMS 注入 PEM",
                    properties.getKid());
        } catch (Exception e) {
            throw new IllegalStateException("RSA 密钥初始化失败", e);
        }
    }

    public String getKid() {
        return properties.getKid();
    }

    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        byte[] der = pemToDer(pem, "PRIVATE KEY");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey parsePublicKey(String pem) throws Exception {
        byte[] der = pemToDer(pem, "PUBLIC KEY");
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] pemToDer(String pem, String marker) {
        String body = pem.replaceAll("-----BEGIN " + marker + "-----", "")
                .replaceAll("-----END " + marker + "-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }
}
