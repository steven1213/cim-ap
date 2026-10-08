package com.cim.iam.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IAM 登录认证配置（{@code cim.iam.auth.*}）。
 *
 * <p>与 {@code cim.iam.jwt.*}（签发配置）并列，承载「登录」一侧的配置：
 * <ul>
 *   <li>{@code source}：认证源类型（{@code local} / {@code ldap}）；</li>
 *   <li>{@code password}：两层派生参数（pepper 为服务端密钥，<b>不入库</b>）；</li>
 *   <li>{@code ldap}：AD/LDAP 连接参数（仅 {@code source=ldap} 时生效）。</li>
 * </ul>
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.auth")
public class IamAuthProperties {

    private String source = "local";
    private Password password = new Password();
    private Ldap ldap = new Ldap();
    private Lockout lockout = new Lockout();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Password getPassword() {
        return password;
    }

    public void setPassword(Password password) {
        this.password = password;
    }

    public Ldap getLdap() {
        return ldap;
    }

    public void setLdap(Ldap ldap) {
        this.ldap = ldap;
    }

    public Lockout getLockout() {
        return lockout;
    }

    public void setLockout(Lockout lockout) {
        this.lockout = lockout;
    }

    public static class Password {
        private String pepper = "";
        private int rounds = 100_000;

        public String getPepper() {
            return pepper;
        }

        public void setPepper(String pepper) {
            this.pepper = pepper;
        }

        public int getRounds() {
            return rounds;
        }

        public void setRounds(int rounds) {
            this.rounds = rounds;
        }
    }

    public static class Ldap {
        private String url = "ldap://localhost:389";
        private String userDnPattern = "{0}";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.ldapUrlGuard(url);
            this.url = url;
        }

        public String getUserDnPattern() {
            return userDnPattern;
        }

        public void setUserDnPattern(String userDnPattern) {
            this.userDnPattern = userDnPattern;
        }

        private void ldapUrlGuard(String url) {
            // 仅作占位校验点，避免误配 http 等协议；保留扩展空间
            if (url != null && !url.startsWith("ldap")) {
                throw new IllegalArgumentException("ldap.url 须以 ldap:// 或 ldaps:// 开头");
            }
        }
    }

    /**
     * 登录失败锁定（暴力破解防护）。
     *
     * <p>连续失败达到 {@link #maxAttempts} 次即锁定账户 {@link #lockMinutes} 分钟；
     * 窗口 {@link #windowMinutes} 内的失败计入同一计数，窗口外自动重置（滑动窗口）。
     * 登录成功会清零计数。</p>
     */
    public static class Lockout {
        private int maxAttempts = 5;
        private int lockMinutes = 15;
        private int windowMinutes = 15;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("lockout.max-attempts 必须 >= 1");
            }
            this.maxAttempts = maxAttempts;
        }

        public int getLockMinutes() {
            return lockMinutes;
        }

        public void setLockMinutes(int lockMinutes) {
            if (lockMinutes < 1) {
                throw new IllegalArgumentException("lockout.lock-minutes 必须 >= 1");
            }
            this.lockMinutes = lockMinutes;
        }

        public int getWindowMinutes() {
            return windowMinutes;
        }

        public void setWindowMinutes(int windowMinutes) {
            if (windowMinutes < 1) {
                throw new IllegalArgumentException("lockout.window-minutes 必须 >= 1");
            }
            this.windowMinutes = windowMinutes;
        }
    }
}
