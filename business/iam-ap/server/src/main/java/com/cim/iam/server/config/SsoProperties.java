package com.cim.iam.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SSO 会话 Cookie 配置（{@code cim.iam.sso.*}）。
 *
 * <p><b>作用</b>：让「在 IAM 登录一次、访问其它已接入 ap（如 RMS）直接进入」成为可能。
 * 机制是在 IAM 后端（8081）登录成功时种下一枚<b>同站共享</b>的 SSO 会话 Cookie；
 * 任一接入 ap 的中继登录页跳回 8081 的 {@code /login} 时，若该 Cookie 仍有效，
 * 后端直接为请求方应用签发令牌并回跳，<b>不再要求重复输入口令</b>。</p>
 *
 * <p><b>跨端口/跨子域共享原理</b>：Cookie 按「域 + 路径」作用，<b>与端口无关</b>。
 * 因此 8081 种下的 {@code Domain=localhost}（或 host-only）Cookie 会被浏览器在访问
 * {@code localhost:5174} 等其它端口时一并带向 8081（同站顶级导航）。生产若 IAM 与各 ap
 * 分处 {@code iam.example.com} / {@code rms.example.com} 等子域，须显式设
 * {@code cookie-domain: .example.com} 才能跨子域共享。</p>
 *
 * <p><b>安全</b>：Cookie 为 {@code HttpOnly}（SPA 不可读，防 XSS 窃取）+ {@code SameSite=Lax}
 * （顶级导航携带，满足中继回跳；阻断跨站 POST 携带，缓解 CSRF）。
 * {@code secure} 默认 false（localhost http 需关闭）；生产 HTTPS 务必置 true。
 * 回跳地址（{@code redirect}）仅允许 {@code allowed-redirect-hosts} 内的主机，避免开放重定向。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.sso")
public class SsoProperties {

    /** Cookie 名称。 */
    private String cookieName = "iam_sso";

    /** SSO 会话有效期（分钟）。默认 8 小时，作为「一次登录、多 ap 复用」的滑动窗口上界。 */
    private long cookieTtlMinutes = 480;

    /**
     * Cookie 作用域。留空 = host-only（仅本机同端口族共享，localhost 开发足够）；
     * 生产跨子域共享须设为 {@code .example.com} 形式。
     */
    private String cookieDomain = "";

    /** 仅 HTTPS 传输（生产务必 true）。localhost http 下必须 false 否则 Cookie 不被发送。 */
    private boolean cookieSecure = false;

    /** 允许回跳的主机白名单（避免开放重定向）。localhost 开发默认放行。 */
    private List<String> allowedRedirectHosts = List.of("localhost", "127.0.0.1");

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }

    public long getCookieTtlMinutes() {
        return cookieTtlMinutes;
    }

    public void setCookieTtlMinutes(long cookieTtlMinutes) {
        this.cookieTtlMinutes = cookieTtlMinutes;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }

    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public List<String> getAllowedRedirectHosts() {
        return allowedRedirectHosts;
    }

    public void setAllowedRedirectHosts(List<String> allowedRedirectHosts) {
        this.allowedRedirectHosts = allowedRedirectHosts;
    }
}
