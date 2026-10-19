package com.cim.approval.server.notification;

import com.cim.approval.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 站内渠道：复用既有 SSE 推送（内存级，单实例）。
 *
 * <p>透传事件原 payload，并补充 type/title/body，保持前端 SSE 契约不变。</p>
 */
@Component
@RequiredArgsConstructor
public class InAppChannel implements NotificationChannel {

    private final NotificationService notificationService;
    private final NotificationTemplateService templateService;

    @Override
    public ChannelType getType() {
        return ChannelType.IN_APP;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(NotificationEvent event) {
        var rendered = templateService.render(event, "zh-CN");
        for (String user : event.recipients()) {
            if (user == null || user.isBlank()) continue;
            Map<String, Object> payload = new HashMap<>(event.vars() == null ? Map.of() : event.vars());
            payload.putIfAbsent("type", event.type().name());
            payload.put("title", rendered.title());
            payload.put("body", rendered.body());
            notificationService.pushToUser(user, event.type().name(), payload);
        }
    }
}
