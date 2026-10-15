package com.cim.iam.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IAM Web 端配置（{@code cim.iam.web.*}）。
 *
 * <p>承载前端（business/iam-ap/web，开发端口 5171）跨域白名单等面向 Web 端的配置。</p>
 *
 * <p><b>为什么还要放行业务 ap 的控制台源</b>：IAM 是 IdP，业务 ap（如 RMS，开发端口 5174）作为 RP
 * 需要在浏览器里直接调 IAM 的 {@code POST /api/v1/logout} 完成<b>服务端吊销</b>（RP-initiated logout，
 * 业界惯例）。该跨域请求要带 SSO 会话 Cookie（{@code credentials: 'include'}），因此对应源必须在
 * 白名单里——不配则浏览器直接拦掉，表现为「登出后令牌仍有效」。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.web")
public class IamWebProperties {

    /** 允许跨域访问的前端源（生产按部署域名收紧；须列全所有接入 ap 的控制台源）。 */
    private List<String> allowedOrigins = List.of("http://localhost:5171", "http://localhost:5174");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
