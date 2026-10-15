package com.cim.approval.sdk.dto;

public record CallbackRegistrationRequest(
        String appCode,
        String baseUrl,
        /** 回调签名密钥；为空时由平台生成并返回 */
        String secret
) {
}
