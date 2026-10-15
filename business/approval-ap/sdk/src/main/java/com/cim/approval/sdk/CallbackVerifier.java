package com.cim.approval.sdk;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 回调验签工具。业务方在接收到 approval-ap 的回调时，应使用本工具验证
 * X-Approval-Signature，防止伪造请求。签名规则与平台服务端完全一致：
 * <pre>
 *   signature = HMAC-SHA256(secret, timestamp + "." + body)
 * </pre>
 */
public final class CallbackVerifier {

    private CallbackVerifier() {
    }

    public static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 校验回调签名。
     *
     * @param secret    业务方注册回调时获得的密钥
     * @param timestamp 请求头 X-Approval-Timestamp（毫秒）
     * @param body      请求体原文（UTF-8 字符串）
     * @param signature 请求头 X-Approval-Signature
     * @return 签名是否匹配
     */
    public static boolean verify(String secret, String timestamp, String body, String signature) {
        if (signature == null || timestamp == null || body == null) {
            return false;
        }
        return hmacSha256(secret, timestamp + "." + body).equals(signature);
    }
}
