package com.cim.approval.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Webhook 通知渠道（生产告警落地）：适配企业微信群机器人等
 * {@code {"msgtype":"text","text":{"content":...}}} 协议。
 * 未配置 {@code approval.notify.webhook-url} 时为 no-op。
 * <p>当前用于：回调死信（DEAD）告警；后续可扩展超时升级、驳回通知等。</p>
 */
@Slf4j
@Service
public class WebhookNotifyService {

    @Value("${approval.notify.webhook-url:}")
    private String webhookUrl;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 发送文本告警；失败仅记日志（告警通道自身不得影响主流程）。 */
    public void alert(String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[ALERT] (webhook 未配置，仅记日志) {}", text);
            return;
        }
        try {
            String body = "{\"msgtype\":\"text\",\"text\":{\"content\":"
                    + com.fasterxml.jackson.databind.node.TextNode.valueOf(text).toString() + "}}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                log.warn("webhook 告警响应异常: {} {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("webhook 告警发送失败: {}", e.getMessage());
        }
    }
}
