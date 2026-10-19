package com.cim.approval.server.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知分发器（G7 核心）：将事件路由到所有已启用且声明支持该事件的渠道。
 *
 * <p>单渠道失败不影响其他渠道，失败统一降级为日志并记入 {@link NotificationDeadLetter}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;
    private final NotificationDeadLetter deadLetter;

    public void dispatch(NotificationEvent event) {
        for (NotificationChannel ch : channels) {
            if (!ch.isEnabled()) continue;
            if (!ch.supportedEvents().contains(event.type())) continue;
            try {
                ch.send(event);
            } catch (Exception e) {
                log.error("[notify] channel {} failed for {}: {}", ch.getType(), event.type(), e.getMessage());
                deadLetter.record(event, ch.getType(), e.getMessage());
            }
        }
    }
}
