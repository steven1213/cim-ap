package com.cim.iam.server.auth;

/**
 * 认证源 SPI（design.md §8 边界表：企业内登录属 IAM）。
 *
 * <p>不同认证后端实现本接口，由 {@link LoginService} 按 {@code cim.iam.auth.source}
 * 选择匹配的实例。当前提供：
 * <ul>
 *   <li>{@code local} —— 本地凭证表 + 口令两层派生（开发 / 无 AD 环境 / 服务账号）；</li>
 *   <li>{@code ldap} —— 对接企业 LDAP / Active Directory 做 simple bind。</li>
 * </ul>
 * 接入方可按需新增（如 OIDC），无需改动 {@link LoginService}。</p>
 */
public interface AuthenticationSource {

    /** 认证源类型，须与 {@code cim.iam.auth.source} 取值一致。 */
    String sourceType();

    /** 执行一次身份认证。 */
    AuthenticationResult authenticate(LoginCredentials creds);
}
