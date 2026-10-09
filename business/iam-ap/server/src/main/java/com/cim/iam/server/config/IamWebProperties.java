package com.cim.iam.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IAM Web 端配置（{@code cim.iam.web.*}）。
 *
 * <p>承载前端（business/iam-ap/web，开发端口 5171）跨域白名单等面向 Web 端的配置。</p>
 */
@Component
@ConfigurationProperties(prefix = "cim.iam.web")
public class IamWebProperties {

    /** 允许跨域访问的前端源（生产按部署域名收紧）。 */
    private List<String> allowedOrigins = List.of("http://localhost:5171");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
