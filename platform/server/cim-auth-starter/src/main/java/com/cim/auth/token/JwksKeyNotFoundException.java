package com.cim.auth.token;

/**
 * JWKS 中找不到指定 kid 的公钥（可能密钥已轮换但本地缓存未更新）。
 */
public class JwksKeyNotFoundException extends RuntimeException {

    public JwksKeyNotFoundException(String kid) {
        super("JWKS public key not found for kid: " + kid);
    }
}
