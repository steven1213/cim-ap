package com.cim.approval.server.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 哈希链工具：审计记录的不可篡改证据链基础。
 *
 * <p>每条审计记录的 hash = SHA-256(prevHash | businessKey | action | actor |
 * beforeState | afterState | detail | timestamp)，首条 prevHash 取固定盐值。
 * 任何历史记录的篡改都会破坏其后所有 hash 链（验证时从头重算比对即可发现）。</p>
 */
public final class HashChainUtil {

    public static final String GENESIS = "APPROVAL_GENESIS";

    private HashChainUtil() {
    }

    public static String hashEvent(String prevHash, String businessKey, String action, String actor,
                                   String beforeState, String afterState, String detail, long timestamp) {
        String raw = String.join("|",
                nullToEmpty(prevHash),
                nullToEmpty(businessKey),
                nullToEmpty(action),
                nullToEmpty(actor),
                nullToEmpty(beforeState),
                nullToEmpty(afterState),
                nullToEmpty(detail),
                String.valueOf(timestamp));
        return sha256(raw);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
