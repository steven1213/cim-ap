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
 * 令牌版本失效判定器——对接 IAM 版本端点（design.md §8.1(g) 验证端）。
 *
 * <p>消费侧实现：业务 ap 引入本 checker 后，每请求携带的令牌 {@code ver} 与 IAM 维护的
 * 当前用户版本号比对；不一致（即 IAM 在改权限 / 改密 / 强制下线后 bump 了版本）即判定令牌失效 → 401。
 * 通过本地缓存 + TTL 避免每请求回查 IAM（§8.1(a)）。</p>
 *
 * <p>端点约定（由 {@code business/iam-ap} 提供）：<br>
 * {@code GET {iamBaseUrl}{endpoint}?uid={uid}} → 响应体为该用户当前版本号（纯文本 long）。</p>
 *
 * <p><b>降级策略（fail-open）</b>：端点不可达 / 用户无版本记录 / 令牌无 {@code ver}（旧令牌），
 * 一律放行。版本机制是「主动作废已签发令牌」的增强，不应因 IAM 抖动导致全站拒登；
 * 若需严格失效，可改为 deny，但生产建议配合 IAM 高可用。</p>
 */
public class IamTokenVersionChecker implements TokenVersionChecker {

    private static final Logger log = LoggerFactory.getLogger(IamTokenVersionChecker.class);

    private final String baseUrl;
    private final String endpoint;
    private final HttpClient http;
    private final long cacheTtlMillis;

    /** uid → (版本号, 过期时间戳毫秒)。 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public IamTokenVersionChecker(String baseUrl, String endpoint, Duration cacheTtl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.endpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.cacheTtlMillis = cacheTtl.toMillis();
    }

    @Override
    public boolean isAcceptable(TokenClaims claims) {
        Long tokenVer = claims.version();
        if (tokenVer == null) {
            return true; // 旧令牌无 ver：放行
        }
        String uid = claims.userId();
        if (uid == null) {
            return true; // 无法解析用户：放行
        }
        long current = currentVersion(uid);
        if (current < 0) {
            return true; // IAM 无记录 / 不可达：fail-open 放行
        }
        boolean ok = tokenVer == current;
        if (!ok) {
            log.debug("[token-version] uid={} 令牌 ver={} ≠ 当前 ver={}，判定失效", uid, tokenVer, current);
        }
        return ok;
    }

    /** 取当前用户版本（带本地 TTL 缓存）；异常返回 -1（fail-open）。 */
    private long currentVersion(String uid) {
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(uid);
        if (cached != null && cached.expireAt > now) {
            return cached.version;
        }
        long fetched = fetchFromIam(uid);
        if (fetched >= 0) {
            cache.put(uid, new CacheEntry(fetched, now + cacheTtlMillis));
        }
        return fetched;
    }

    private long fetchFromIam(String uid) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint + "?uid=" + uriEncode(uid)))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                return Long.parseLong(resp.body().trim());
            }
            log.warn("[token-version] IAM 版本端点返回 {}，按 fail-open 处理", resp.statusCode());
            return -1;
        } catch (Exception e) {
            log.warn("[token-version] 拉取 IAM 版本失败（{}），按 fail-open 处理", e.getMessage());
            return -1;
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
        final long version;
        final long expireAt;

        CacheEntry(long version, long expireAt) {
            this.version = version;
            this.expireAt = expireAt;
        }
    }
}
