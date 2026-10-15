package com.cim.rms.server.recipe;

import lombok.extern.slf4j.Slf4j;import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 配方 Body 存储加密（Req D9：敏感工艺参数库内密文存储）。
 *
 * <p>AES-256-GCM；密钥来自配置 {@code rms.crypto.body-key}（32 字节 Base64），
 * <b>不入库</b>。密文格式：{@code Base64(IV[12] || ciphertext+tag)}，每次写入随机 IV。</p>
 *
 * <p><b>IF-COM 完整性语义不变</b>：{@code bodyHash} 始终对<b>明文</b>字节计算——
 * 下发到设备与回读验证的都是明文，加密只是存储层（at rest），传输层与 hash 链路不受影响。</p>
 *
 * <p><b>兼容性</b>：{@code body_enc=0}（存量明文，或未配置密钥）直接读原值；
 * {@code body_enc=1} 解密后返回。未配置密钥时进入<b>明文模式</b>（仅限演示/开发环境），
 * 启动时打 warn——生产必须配置密钥。</p>
 */
@Slf4j
@Component
public class BodyStore {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;

    /** AES-256 密钥；{@code null} 表示明文模式（未配置）。 */
    private final byte[] key;

    private final SecureRandom random = new SecureRandom();

    public BodyStore(@Value("${rms.crypto.body-key:}") String keyB64) {
        if (keyB64 == null || keyB64.isBlank()) {
            this.key = null;
            log.warn("[rms-crypto] rms.crypto.body-key 未配置——D9 Body 加密未启用，Body 以明文入库（仅限演示/开发环境）");
        } else {
            byte[] k = Base64.getDecoder().decode(keyB64.trim());
            if (k.length != 32) {
                throw new IllegalArgumentException("rms.crypto.body-key 须为 32 字节（AES-256）的 Base64，实际 " + k.length + " 字节");
            }
            this.key = k;
            log.info("[rms-crypto] D9 Body 加密已启用（AES-256-GCM）");
        }
    }

    /** 加密是否启用。 */
    public boolean enabled() {
        return key != null;
    }

    /** 写库标记（0=明文 / 1=AES-GCM 密文）。 */
    public int encFlag() {
        return key == null ? 0 : 1;
    }

    /**
     * 明文 Base64 → 库内存储值（启用加密时为密文 Base64，否则原样）。
     * 入参须为合法 Base64（调用方已校验）；空值原样返回。
     */
    public String encryptForStore(String plainB64) {
        if (key == null || plainB64 == null || plainB64.isEmpty()) {
            return plainB64;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(Base64.getDecoder().decode(plainB64));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("D9 Body 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 库内值 → 明文 Base64。兼容存量明文（{@code body_enc} 为 0/null）；
     * 解密失败（密钥轮换不匹配/数据损坏）抛明确错误，绝不静默返回密文。
     */
    public String readPlain(RecipeVersion v) {
        String stored = v.getBodyBase64();
        if (stored == null || stored.isEmpty()
                || v.getBodyEnc() == null || v.getBodyEnc() != 1) {
            return stored;
        }
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            if (all.length <= IV_LEN) {
                throw new IllegalStateException("密文过短");
            }
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, all, 0, IV_LEN));
            byte[] pt = c.doFinal(all, IV_LEN, all.length - IV_LEN);
            return Base64.getEncoder().encodeToString(pt);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "D9 Body 解密失败（密钥不匹配或数据损坏，versionId=" + v.getId() + "）", e);
        }
    }

    /** 就地写入：加密（启用时）并设置 {@code bodyBase64} + {@code bodyEnc}。 */
    public void write(RecipeVersion v, String plainB64) {
        v.setBodyBase64(encryptForStore(plainB64));
        v.setBodyEnc(encFlag());
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException(
                    "读到 body_enc=1 的密文但 rms.crypto.body-key 未配置——请恢复密钥配置（密钥丢失的数据无法恢复）");
        }
    }
}
