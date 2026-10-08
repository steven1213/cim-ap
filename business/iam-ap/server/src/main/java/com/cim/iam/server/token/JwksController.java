package com.cim.iam.server.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWKS 公钥发布端点（RFC 7517，design.md §8.1(d)）。
 *
 * <p>验证端（各业务 ap 的 {@code cim-auth-starter}）通过 {@code cim.auth.jwks-uri} 拉取并按
 * {@code kid} 匹配 RSA 公钥做本地验签；公钥轮换时改 {@code kid} 即可，验证端按 TTL 重拉。</p>
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final RsaKeyService rsaKeyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping(value = "/.well-known/jwks.json", produces = "application/json")
    public Map<String, Object> jwks() {
        RSAPublicKey pub = (RSAPublicKey) rsaKeyService.getPublicKey();
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("alg", "RS256");
        key.put("use", "sig");
        key.put("kid", rsaKeyService.getKid());
        key.put("n", base64Url(pub.getModulus()));
        key.put("e", base64Url(pub.getPublicExponent()));
        return Map.of("keys", List.of(key));
    }

    private static String base64Url(BigInteger v) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(v.toByteArray());
    }
}
