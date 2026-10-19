package com.cim.approval.server.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信渠道配置。未配置 {@code enabled=true} 时 {@link WeComChannel} 降级为日志，不发起真实网络请求。
 *
 * <p>机器人协议已在死信告警中验证（D3），故默认采用群机器人 webhook 投递；
 * 后续如需应用消息推送可扩展为 corp/agent 模式。</p>
 */
@Component
@ConfigurationProperties(prefix = "approval.notification.wecom")
public class WeComProperties {

    private boolean enabled = false;
    private String webhookUrl = "";
    private final Sign sign = new Sign();

    public boolean isEnabled() {
        return enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public Sign getSign() {
        return sign;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public static class Sign {
        private String secret = "dev-sign-secret-change-me";
        private int ttlMinutes = 15;

        public String getSecret() {
            return secret;
        }

        public int getTtlMinutes() {
            return ttlMinutes;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public void setTtlMinutes(int ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }
}
