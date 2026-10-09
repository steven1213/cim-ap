package com.cim.iam.server.config;

import com.cim.auth.token.JwksKeyNotFoundException;
import com.cim.auth.token.JwksKeyProvider;
import com.cim.iam.server.token.RsaKeyService;

import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

/**
 * IAM 自签发令牌的本地验签密钥提供器（覆盖 cim-auth-starter 默认的 HTTP JWKS 提供器）。
 *
 * <p>IAM 自身就是签发方，公钥已在 {@link RsaKeyService} 中（内存里）；无需经 HTTP 回拉
 * {@code /.well-known/jwks.json}，直接读取即可。这样 IAM 验证自己签发的令牌走 in-JVM 路径，
 * 既避免环回网络依赖，也让 MockMvc 测试无需真实监听端口即可完成验签。</p>
 *
 * <p>starter 的 {@code JwksKeyProvider} 是 {@code @ConditionalOnMissingBean}，本类作为子类经
 * {@link IamSecurityConfig#iamJwksKeyProvider(RsaKeyService)} 注册为 Bean，顶替默认实现
 * （ADR：验证侧「让位」机制）。</p>
 */
public class IamJwksKeyProvider extends JwksKeyProvider {

    private final RsaKeyService rsaKeyService;

    public IamJwksKeyProvider(RsaKeyService rsaKeyService) {
        // 父类按 kid 缓存 JWKS；IAM 单密钥场景不依赖远程拉取，构造一个惰性 Supplier 占位，
        // 实际公钥由下方 getPublicKey 直接读内存公钥返回。
        super(() -> "{\"keys\":[]}", Duration.ofHours(1));
        this.rsaKeyService = rsaKeyService;
    }

    @Override
    public RSAPublicKey getPublicKey(String kid) {
        PublicKey pk = rsaKeyService.getPublicKey();
        if (!(pk instanceof RSAPublicKey rk)) {
            throw new JwksKeyNotFoundException(kid == null ? "iam" : kid);
        }
        return rk;
    }
}
