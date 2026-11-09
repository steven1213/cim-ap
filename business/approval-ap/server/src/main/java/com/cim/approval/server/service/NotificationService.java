package com.cim.approval.server.service;

import com.cim.notify.api.NotifyEvent;
import com.cim.notify.api.NotifyPublisher;
import com.cim.notify.config.NotifyProperties;
import com.cim.spring.support.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 实时通知：基于 {@code cim-notify-starter} 的统一 SSE 推送（多实例经 Redis 扇出）。
 *
 * <p>取代原先的内存级 SseEmitter Map（单实例不可上生产）。{@link NotifyPublisher} 负责扇出，
 * 本服务只负责把业务事件转换为 {@link NotifyEvent}。租户取自 {@link TenantContext}（通知分发在
 * 请求线程内触发，租户上下文已写入）。</p>
 *
 * <p>红点（未读数）由各 AP 自有持久表负责（见 cim-notify-starter 设计稿 §0 铁律）：SSE 仅做在线实时
 * 提醒，离线/重连补差由前端回拉各 AP 的 /unread-count 完成。approval-ap 当前未持久化通知，
 * 如需要红点历史，后续按各 AP 自管原则补一张通知表。</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotifyPublisher notifyPublisher;
    private final ObjectMapper objectMapper;
    private final NotifyProperties notifyProperties;

    public void pushToUser(String userId, String eventType, Object payload) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String tenant = TenantContext.get();
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            json = String.valueOf(payload);
        }
        notifyPublisher.publish(new NotifyEvent(tenant, NotifyEvent.TargetType.USER, List.of(userId),
                notifyProperties.getAppCode(), eventType, json));
    }

    public void pushEvent(Map<String, Object> event) {
        Object user = event.get("userId");
        if (user != null) {
            pushToUser(String.valueOf(user), String.valueOf(event.get("type")), event);
        }
    }
}
