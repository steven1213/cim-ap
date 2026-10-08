package com.cim.iam.server.auth;

/**
 * 登录入参（与认证源无关的统一载体）。
 *
 * <p>{@code credential} 的语义随认证源而变：
 * <ul>
 *   <li>{@code local} 模式 = 客户端第一层派生后的 {@code clientHash}（明文口令不出网）；</li>
 *   <li>{@code ldap} 模式 = 经 TLS 传输的明文口令，用于向 AD/LDAP 做 simple bind。</li>
 * </ul>
 * {@code clientSalt} 仅作信息携带（本地模式由服务端 {@code GET /api/v1/login/salt} 下发）。</p>
 *
 * @param username   用户名（AD 账号 / 本地账号）
 * @param credential 见上，随认证源解释
 * @param clientSalt 客户端盐（本地模式用，可为空）
 */
public record LoginCredentials(String username, String credential, String clientSalt) {
}
