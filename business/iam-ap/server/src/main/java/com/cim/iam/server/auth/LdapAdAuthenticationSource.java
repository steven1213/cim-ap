package com.cim.iam.server.auth;

import com.cim.iam.server.config.IamAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/**
 * LDAP / Active Directory 认证源（{@code cim.iam.auth.source=ldap} 时启用）。
 *
 * <p>员工身份由企业 AD/LDAP 托管，IAM 不重建目录：以用户提供的明文口令向目录做
 * <b>simple bind</b> 完成校验。生产须使用 {@code ldaps://}（隐式 TLS）保护口令传输；
 * {@code userDnPattern} 支持整段主体模板（如 {@code uid={0},ou=people,dc=corp,dc=com}，
 * 或 AD 常用 {@code {0}@corp.example.com} 形式的 userPrincipalName）。</p>
 *
 * <p><b>与两层派生的关系</b>：AD 需要明文口令才能 bind，故 LDAP 模式不采用客户端散列，
 * 改由 TLS 承担传输安全；两层派生是「本地凭证」策略。两者按 {@code source} 切换，互不耦合。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cim.iam.auth", name = "source", havingValue = "ldap")
public class LdapAdAuthenticationSource implements AuthenticationSource {

    private final IamAuthProperties.Ldap ldap;

    public LdapAdAuthenticationSource(IamAuthProperties properties) {
        this.ldap = properties.getLdap();
    }

    @Override
    public String sourceType() {
        return "ldap";
    }

    @Override
    public AuthenticationResult authenticate(LoginCredentials creds) {
        String principal = ldap.getUserDnPattern().replace("{0}", creds.username());
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldap.getUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, creds.credential());
        InitialDirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            return AuthenticationResult.success(creds.username(), creds.username());
        } catch (NamingException e) {
            log.warn("[auth-ldap] bind 失败 user={}", creds.username());
            return AuthenticationResult.failure("AD/LDAP 认证失败");
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                    // 关闭失败不影响认证结论
                }
            }
        }
    }
}
