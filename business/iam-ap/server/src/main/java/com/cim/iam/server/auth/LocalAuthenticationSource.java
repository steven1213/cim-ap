package com.cim.iam.server.auth;

import com.cim.iam.server.config.IamAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地认证源（{@code cim.iam.auth.source=local}，默认启用）。
 *
 * <p>凭证表 {@link LocalCredential} 仅存第二层派生散列；本源重新派生并恒定时间比对，
 * 全程不接触明文口令。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cim.iam.auth", name = "source", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalAuthenticationSource implements AuthenticationSource {

    private final LocalCredentialService credentialService;
    private final IamAuthProperties properties;

    @Override
    public String sourceType() {
        return "local";
    }

    @Override
    public AuthenticationResult authenticate(LoginCredentials creds) {
        LocalCredential c = credentialService.findByUsername(creds.username()).orElse(null);
        if (c == null || !c.isEnabled()) {
            return AuthenticationResult.failure("用户不存在或已禁用");
        }
        String candidate = PasswordDerivation.derive(
                creds.credential(),
                properties.getPassword().getPepper(),
                properties.getPassword().getRounds());
        if (!PasswordDerivation.constantTimeEquals(candidate, c.getServerHash())) {
            return AuthenticationResult.failure("口令校验失败");
        }
        return AuthenticationResult.success(c.getUserId(), c.getUsername());
    }
}
