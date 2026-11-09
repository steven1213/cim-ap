package com.cim.rms.server.notification;

import com.cim.notify.api.NotifyEvent;
import com.cim.notify.api.NotifyPublisher;
import com.cim.notify.config.NotifyProperties;
import com.cim.spring.support.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SSE 实时推送渠道（cim-notify-starter）：落库后即时把通知经统一 SSE 通道推给在线用户。
 *
 * <p>与 {@link InAppNotifyChannel}（落库即达，红点真相在持久表 {@code rms_notification}）并列，
 * 互不干扰；任一渠道失败只记日志，<b>绝不</b>让通知失败拖垮业务事务（签核提交等是主事务）。</p>
 *
 * <p><b>目标解析</b>：
 * <ul>
 *   <li>有指定收件人 {@code recipient} → {@link NotifyEvent.TargetType#USER}；</li>
 *   <li>受众广播 {@code audience}（APPROVER/INTEGRATION）→ 映射为对应权限码 {@link NotifyEvent.TargetType#ROLE}
 *       （用户 JWT 携带该权限码即命中，对齐 RMS 内部 RBAC；未命中映射时回退为原始 audience 串）；</li>
 *   <li>全员广播（无 recipient 无 audience）→ 跳过 SSE，由前端轮询 /unread-count 兜底
 *       （SSE 刷新/重连必然丢消息，红点真相以持久表为准）。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseNotifyChannel implements NotifyChannel {

    private final NotifyPublisher notifyPublisher;
    private final NotifyProperties notifyProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "SSE";
    }

    @Override
    public void deliver(RmsNotification n) {
        try {
            String tenant = TenantContext.get();
            String payload = objectMapper.writeValueAsString(toPayload(n));
            if (n.getRecipient() != null && !n.getRecipient().isBlank()) {
                notifyPublisher.publish(new NotifyEvent(tenant, NotifyEvent.TargetType.USER,
                        List.of(n.getRecipient()), notifyProperties.getAppCode(), n.getType(), payload));
            } else if (n.getAudience() != null && !n.getAudience().isBlank()) {
                String role = NotificationService.AUDIENCE_PERMS.getOrDefault(n.getAudience(), n.getAudience());
                notifyPublisher.publish(new NotifyEvent(tenant, NotifyEvent.TargetType.ROLE,
                        List.of(role), notifyProperties.getAppCode(), n.getType(), payload));
            } else {
                log.debug("[notify] 全员广播跳过 SSE（红点靠轮询兜底） type={}", n.getType());
            }
        } catch (JsonProcessingException e) {
            log.warn("[notify] SSE 渠道序列化失败（不影响业务）: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[notify] SSE 渠道投递失败（不影响业务）: {}", e.toString());
        }
    }

    /** 推送给前端的精简负载（与持久冗余字段脱敏，仅取展示所需）。 */
    private record SsePayload(String id, String type, String title, String content,
                              String refType, String refId, String occurredAt) {
    }

    private SsePayload toPayload(RmsNotification n) {
        return new SsePayload(n.getId(), n.getType(), n.getTitle(), n.getContent(),
                n.getRefType(), n.getRefId(), fmt(n.getOccurredAt()));
    }

    private static String fmt(Instant t) {
        return t == null ? null
                : LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
