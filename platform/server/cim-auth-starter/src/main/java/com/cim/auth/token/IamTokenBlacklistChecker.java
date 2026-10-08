package com.cim.auth.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌黑名单判定器——对接 IAM 黑名单端点（design.md §8.1(h) 验证端）。
 *
 * <p>消费侧实现：业务 ap 引入本 checker 后，每请求携带的令牌 {@code jti} 与 IAM 黑名单比对；
 * 命中（用户已登出 / 主动吊销）即判定失效 → 401。通过本地缓存 + TTL 避免每请求回查 IAM（§8.1(a)）。</p>
 *
 * <p>端点约定（由 {@code business/iam-ap} 提供）：<br>
 * {@code GET {iamBaseUrl}{endpoint}?jti={jti}} → 响应体为该 jti 是否已被拉黑（{@code true}/{@code false}）。</p>
 *
 * <p><b>降级策略（fail-open）</b>：端点不可达 / 令牌无 {@code jti}（旧令牌）/ 响应非 {@code true}，
 * 一律放行。黑名单是「主动吊销」增强，不应因 IAM 抖动导致全站拒登；若需严格失效可改为 deny（生产建议
 * 配合 IAM 高可用）。与版本 checker 一致。</p>
 */
public class IamTokenBlacklistChecker implements TokenBlacklistChecker {

    private static final Logger log = LoggerFactory.getLogger(IamTokenBlacklistChecker.class);

    private final String baseUrl;
    private final String endpoint;
    private final HttpClient http;
    private final long cacheTtlMillis;

    /** jti → (是否已拉黑, 过期时间戳毫秒)。 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public IamTokenBlacklistChecker(String baseUrl, String endpoint, Duration cacheTtl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.endpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.cacheTtlMillis = cacheTtl.toMillis();
    }

    @Override
    public boolean isAcceptable(TokenClaims claims) {
        String jti = claims.jti();
        if (jti == null) {
            return true; // 旧令牌无 jti：放行（版本机制仍可覆盖）
        }
        Boolean revoked = isRevoked(jti);
        if (revoked == null) {
            return true; // IAM 不可达：fail-open 放行
        }
        if (revoked) {
            log.debug("[token-blacklist] jti={} 已被拉黑，判定失效", jti);
            return false;
        }
        return true;
    }

    /** 取 jti 是否被拉黑（带本地 TTL 缓存）；异常返回 null（fail-open）。 */
    private Boolean isRevoked(String jti) {
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(jti);
        if (cached != null && cached.expireAt > now) {
            return cached.revoked;
        }
        Boolean fetched = fetchFromIam(jti);
        if (fetched != null) {
            cache.put(jti, new CacheEntry(fetched, now + cacheTtlMillis));
        }
        return fetched;
    }

    private Boolean fetchFromIam(String jti) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint + "?jti=" + uriEncode(jti)))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                return Boolean.parseBoolean(resp.body().trim());
            }
            log.warn("[token-blacklist] IAM 黑名单端点返回 {}，按 fail-open 处理", resp.statusCode());
            return null;
        } catch (Exception e) {
            log.warn("[token-blacklist] 拉取 IAM 黑名单失败（{}），按 fail-open 处理", e.getMessage());
            return null;
        }
    }

    private static String uriEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static final class CacheEntry {
        final boolean revoked;
        final long expireAt;

        CacheEntry(boolean revoked, long expireAt) {
            this.revoked = revoked;
            this.expireAt = expireAt;
        }
    }
}
