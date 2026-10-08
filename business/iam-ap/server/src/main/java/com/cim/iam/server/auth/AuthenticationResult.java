package com.cim.iam.server.auth;

/**
 * 认证源对一次登录尝试的结论。
 *
 * @param success  是否通过
 * @param userId   通过时的用户唯一标识（对应令牌 {@code uid}/{@code sub}）
 * @param username 通过时的用户名（对应令牌 {@code uname}）
 * @param reason   失败原因（成功时为 {@code null}）
 */
public record AuthenticationResult(boolean success, String userId, String username, String reason) {

    public static AuthenticationResult success(String userId, String username) {
        return new AuthenticationResult(true, userId, username, null);
    }

    public static AuthenticationResult failure(String reason) {
        return new AuthenticationResult(false, null, null, reason);
    }
}
