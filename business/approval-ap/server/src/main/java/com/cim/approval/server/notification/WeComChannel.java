package com.cim.approval.server.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 企业微信渠道（D3 第一渠道 / G7）。
 *
 * <p>采用群机器人 webhook 投递 markdown 消息（机器人协议已在死信告警中验证）。
 * 配置 {@code approval.notification.wecom.enabled=true} 且填好 webhook-url 才发起真实请求；
 * 否则降级为日志，便于无凭证联调且不污染生产。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeComChannel implements NotificationChannel {

    private final WeComProperties props;
    private final NotificationTemplateService templateService;
    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public ChannelType getType() {
        return ChannelType.WECOM;
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled() && props.getWebhookUrl() != null && !props.getWebhookUrl().isBlank();
    }

    @Override
    public Set<NotificationEvent.Type> supportedEvents() {
        return Set.of(NotificationEvent.Type.NEW_TASK, NotificationEvent.Type.TASK_DONE,
                NotificationEvent.Type.TASK_OVERDUE, NotificationEvent.Type.URGE);
    }

    @Override
    public void send(NotificationEvent event) {
        var rendered = templateService.render(event, "zh-CN");
        String content = "**" + rendered.title() + "**\n" + rendered.body();
        if (!isEnabled()) {
            log.warn("[WeComChannel][DEV-FALLBACK] would push to {}: {}", event.recipients(), content);
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "msgtype", "markdown",
                    "markdown", Map.of("content", content)));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(props.getWebhookUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new RuntimeException("wecom webhook http " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("wecom send failed: " + e.getMessage(), e);
        }
    }
}
