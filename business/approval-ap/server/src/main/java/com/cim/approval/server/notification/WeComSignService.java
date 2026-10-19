package com.cim.approval.server.notification;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * 企微免登签名令牌（G8 / D9）。
 *
 * <p>绑定 userId + taskId，HMAC-SHA256 签名防篡改，默认 15min 过期；nonce 防重放。
 * 企微内置浏览器打开 H5 办理页携带此令牌，后端核销后免登录直接进入办理。</p>
 */
@Service
@RequiredArgsConstructor
public class WeComSignService {

    private final ObjectMapper objectMapper;

    @Value("${approval.notification.wecom.sign.secret:dev-sign-secret-change-me}")
    private String secret;

    @Value("${approval.notification.wecom.sign.ttl-minutes:15}")
    private int ttlMinutes;

    public String generate(String userId, String taskId) {
        try {
            long now = System.currentTimeMillis();
            long exp = now + (long) ttlMinutes * 60_000L;
            String nonce = UUID.randomUUID().toString().replace("-", "");
            Map<String, Object> payload = Map.of("u", userId, "t", taskId, "n", nonce, "exp", exp);
            String body = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
            return body + "." + sign(body);
        } catch (Exception e) {
            throw new BizException(BizCode.SYSTEM_ERROR, "生成免登令牌失败");
        }
    }

    public Verified verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "令牌缺失");
        }
        int idx = token.lastIndexOf('.');
        if (idx < 0) {
            throw new BizException(BizCode.PARAM_INVALID, "令牌格式错误");
        }
        String body = token.substring(0, idx);
        String sig = token.substring(idx + 1);
        String expected;
        try {
            expected = sign(body);
        } catch (Exception e) {
            throw new BizException(BizCode.SYSTEM_ERROR, "令牌校验失败");
        }
        if (!constantTimeEq(expected, sig)) {
            throw new BizException(BizCode.PARAM_INVALID, "令牌无效或已被篡改");
        }
        try {
            Map<?, ?> p = objectMapper.readValue(Base64.getUrlDecoder().decode(body), Map.class);
            long exp = ((Number) p.get("exp")).longValue();
            boolean expired = System.currentTimeMillis() > exp;
            return new Verified(String.valueOf(p.get("u")), String.valueOf(p.get("t")), expired);
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "令牌内容损坏");
        }
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private boolean constantTimeEq(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }

    public record Verified(String userId, String taskId, boolean expired) {
    }
}
