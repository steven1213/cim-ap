package com.cim.iam.server.auth;

import com.cim.core.port.IdGenerator;
import com.cim.iam.server.config.IamTokenProperties;
import com.cim.iam.server.token.TokenIssuerService;
import com.cim.iam.server.token.TokenIssueRequest;
import com.cim.spring.support.web.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 刷新令牌服务（design.md §8.1(h) 刷新流程）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>签发时仅返回一次明文，库内只存 {@code SHA-256} 散列（明文不落库）；</li>
 *   <li>每次刷新轮转都会作废旧刷新令牌（防重放），并重签访问令牌（携带最新 {@code ver}）；</li>
 *   <li>校验失败（不存在 / 已作废 / 过期）统一抛 {@code noAdmission}，由全局异常映射为 403。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final IdGenerator idGenerator;
    private final TokenIssuerService tokenIssuerService;
    private final IamTokenProperties tokenProperties;

    private static final SecureRandom RNG = new SecureRandom();

    /**
     * 签发刷新令牌（登录时调用）。返回明文（仅此一次）。
     *
     * @param userId       用户 ID
     * @param accessToken  本次签发的访问令牌（用于提取 jti 关联，便于审计）
     */
    @Transactional
    public String issue(String userId, String accessToken) {
        String plaintext = generateOpaque();
        RefreshToken rt = new RefreshToken();
        rt.setId(idGenerator.nextId());
        rt.setUserId(userId);
        rt.setTokenHash(hash(plaintext));
        rt.setAccessTokenJti(jtiOf(accessToken));
        rt.setExpiresAt(Instant.now().plus(Duration.ofMinutes(tokenProperties.getRefreshTokenTtlMinutes())));
        rt.setRevoked(false);
        repository.save(rt);
        return plaintext;
    }

    /**
     * 用刷新令牌换取新访问令牌 + 新刷新令牌（轮转）。
     * 旧刷新令牌立即作废（防重放）。失败抛 {@code noAdmission}。
     */
    @Transactional
    public RefreshedTokens rotate(String plaintextRefreshToken) {
        RefreshToken rt = repository.findByTokenHash(hash(plaintextRefreshToken))
                .orElseThrow(() -> BizException.noAdmission("刷新令牌无效或已失效"));
        if (rt.isRevoked() || rt.getExpiresAt().isBefore(Instant.now())) {
            throw BizException.noAdmission("刷新令牌无效或已失效");
        }
        rt.setRevoked(true);
        rt.setRevokedAt(Instant.now());
        repository.save(rt);

        String userId = rt.getUserId();
        String accessToken = tokenIssuerService.issue(new TokenIssueRequest(userId, null, null));
        String newRefresh = issue(userId, accessToken);
        log.info("[refresh] 轮转成功 user={}", userId);
        return new RefreshedTokens(accessToken, newRefresh);
    }

    /** 撤销刷新令牌（登出时调用；找不到则静默）。 */
    @Transactional
    public void revoke(String plaintextRefreshToken) {
        if (plaintextRefreshToken == null || plaintextRefreshToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(plaintextRefreshToken)).ifPresent(rt -> {
            rt.setRevoked(true);
            rt.setRevokedAt(Instant.now());
            repository.save(rt);
        });
    }

    /** 从已签发访问令牌（本 IAM 签发）中提取 jti，用于关联刷新令牌。 */
    String jtiOf(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("malformed token");
            }
            String payload = parts[1];
            while (payload.length() % 4 != 0) {
                payload += "=";
            }
            String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            return node.path("jti").asText(null);
        } catch (Exception e) {
            throw new IllegalStateException("无法解析访问令牌 jti", e);
        }
    }

    private String generateOpaque() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return "rt-" + UUID.randomUUID() + "-"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String plaintext) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 轮转结果：新访问令牌 + 新刷新令牌。 */
    public record RefreshedTokens(String accessToken, String refreshToken) {
    }
}
