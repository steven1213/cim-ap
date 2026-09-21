package com.cim.auth.token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 从 IAM 的 JWKS 拉取并按 kid 缓存 RSA 公钥，支持密钥轮换（design.md §2.4 / T3.1）。
 *
 * <p>默认从 {@code jwksUri} 拉取；测试可注入 {@link Supplier<String>} 直接给出 JWKS JSON。
 * 公钥按 kid 缓存在内存，超过 {@code cacheTtl} 后下次访问触发刷新（如 IAM 轮换了签名密钥）。</p>
 */
public class JwksKeyProvider {

    private final Supplier<String> jwksSource;
    private final Duration cacheTtl;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile Map<String, RSAPublicKey> cache;
    private volatile Instant refreshedAt;

    public JwksKeyProvider(String jwksUri, Duration cacheTtl) {
        this(() -> fetch(jwksUri), cacheTtl);
    }

    public JwksKeyProvider(Supplier<String> jwksSource, Duration cacheTtl) {
        this.jwksSource = jwksSource;
        this.cacheTtl = cacheTtl;
    }

    public RSAPublicKey getPublicKey(String kid) {
        if (kid == null || kid.isBlank()) {
            throw new JwksKeyNotFoundException("<null>");
        }
        if (needsRefresh()) {
            refresh();
        }
        RSAPublicKey key = cache.get(kid);
        if (key == null) {
            // 可能刚轮换，再拉一次
            refresh();
            key = cache.get(kid);
        }
        if (key == null) {
            throw new JwksKeyNotFoundException(kid);
        }
        return key;
    }

    private synchronized void refresh() {
        if (!needsRefresh() && cache != null) {
            return;
        }
        Map<String, RSAPublicKey> map = new HashMap<>();
        try {
            JsonNode root = mapper.readTree(jwksSource.get());
            JsonNode keys = root.get("keys");
            if (keys != null) {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                for (JsonNode k : keys) {
                    if (!"RSA".equals(k.path("kty").asText())) {
                        continue;
                    }
                    String kid = k.path("kid").asText();
                    byte[] n = Base64.getUrlDecoder().decode(k.path("n").asText());
                    byte[] e = Base64.getUrlDecoder().decode(k.path("e").asText());
                    RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, n), new BigInteger(1, e));
                    map.put(kid, (RSAPublicKey) kf.generatePublic(spec));
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load JWKS", ex);
        }
        cache = map;
        refreshedAt = Instant.now();
    }

    private boolean needsRefresh() {
        return cache == null || refreshedAt == null
                || Duration.between(refreshedAt, Instant.now()).compareTo(cacheTtl) > 0;
    }

    private static String fetch(String uri) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(uri)).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("JWKS fetch failed with status " + resp.statusCode());
            }
            return resp.body();
        } catch (Exception ex) {
            throw new IllegalStateException("JWKS fetch failed: " + uri, ex);
        }
    }
}
