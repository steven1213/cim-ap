package com.cim.iam.server.token;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.config.IamProperties;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 令牌签发（RS256，design.md §8.1(c) claim 契约）。
 *
 * <p>发出的令牌含 {@code uid/uname/apps/roles/tenantId/ver/exp/iat/sub}，其中：
 * <ul>
 *   <li>{@code apps} 来自 {@link AppRegistrationService#enabledAppsForUser}（准入依据）；</li>
 *   <li>{@code ver} 来自 {@link TokenVersionService#currentVersion}（版本失效判定）；</li>
 *   <li>{@code roles} 为该用户在各 ap 的粗角色组合。</li>
 * </ul>
 * 公钥经 {@link JwksController} 发布，验证端本地验签（不每请求回查 IAM）。</p>
 */
@Service
@RequiredArgsConstructor
public class TokenIssuerService {

    private final RsaKeyService rsaKeyService;
    private final IamProperties properties;
    private final TokenVersionService tokenVersionService;
    private final AppRegistrationService appRegistrationService;

    public String issue(TokenIssueRequest req) {
        PrivateKey key = rsaKeyService.getPrivateKey();
        long now = System.currentTimeMillis();

        long ver = tokenVersionService.currentVersion(req.userId());
        Set<String> apps = appRegistrationService.enabledAppsForUser(req.userId());
        Set<String> roles = appRegistrationService.rolesByAppForUser(req.userId()).values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // jti：令牌唯一标识，用于登出 / 主动吊销时写入黑名单（design.md §8.1(h)）
        String jti = java.util.UUID.randomUUID().toString();

        return Jwts.builder()
                .header().keyId(rsaKeyService.getKid()).and()
                .issuer(properties.getIssuer())
                .subject(req.userId())
                .claim("uid", req.userId())
                .claim("uname", req.username() != null ? req.username() : req.userId())
                .claim("apps", apps)
                .claim("roles", roles)
                .claim("tenantId", req.tenantId())
                .claim("jti", jti)
                .claim("ver", ver)
                .issuedAt(new Date(now))
                .expiration(new Date(now + properties.getAccessTokenTtlMinutes() * 60_000L))
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }
}
