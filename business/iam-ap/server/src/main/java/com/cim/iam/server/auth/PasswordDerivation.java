package com.cim.iam.server.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

/**
 * 口令两层派生工具（design.md §8 边界表 / README §2 口令派生）。
 *
 * <p><b>第一层（客户端 / 前端）</b>：{@code clientHash = derive(password, clientSalt, rounds)}。
 * 明文口令不出浏览器，仅传 {@code clientHash} 与 {@code clientSalt}。</p>
 * <p><b>第二层（服务端）</b>：{@code serverHash = derive(clientHash, serverPepper, rounds)}。
 * 入库仅存 {@code serverHash}（与每用户 {@code clientSalt}）；{@code serverPepper} 为服务端密钥（配置文件 / KMS），
 * <b>不入库</b>，因此即便凭证表泄露也无法反推口令。</p>
 *
 * <p>算法钉死 {@code PBKDF2WithHmacSHA256}，与平台「算法钉死」质量门禁一致。</p>
 */
public final class PasswordDerivation {

    private static final String ALGO = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ROUNDS = 100_000;
    private static final int KEY_BITS = 256;

    private PasswordDerivation() {
    }

    /** 单层 PBKDF2 派生，返回十六进制串。 */
    public static String derive(String input, String salt, int rounds) {
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGO);
            PBEKeySpec spec = new PBEKeySpec(
                    input.toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8),
                    rounds,
                    KEY_BITS);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (InvalidKeySpecException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("PBKDF2 派生失败", e);
        }
    }

    /** 恒定时间比较，避免时序攻击。 */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < ba.length; i++) {
            r |= (ba[i] ^ bb[i]);
        }
        return r == 0;
    }
}
