package com.cim.iam.server.token;

/**
 * 令牌签发请求（IAM 内部签发接口用）。
 *
 * <p><b>注意</b>：生产签发前必须由真实登录流程（LDAP/AD 对接 + 口令前端加密 / 服务端二次派生）
 * 完成身份校验；本结构仅承载「已认证身份 → 令牌」的入参。登录流程属后续里程碑（M-login）。</p>
 *
 * @param userId   用户唯一标识（对应令牌 {@code uid} / {@code sub}）
 * @param username 用户名（AD 账号，对应 {@code uname}）
 * @param tenantId 租户 ID（可空）
 */
public record TokenIssueRequest(String userId, String username, String tenantId) {
}
