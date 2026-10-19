package com.cim.approval.server.notification;

import java.util.Set;

/**
 * 通知渠道 SPI（适配器模式，对齐初稿 §4.11）。
 *
 * <p>实现类以 Spring Bean 注册，由 {@link NotificationDispatcher} 统一路由。
 * {@link #supportedEvents()} 声明本渠道处理的事件子集，未列出的事件不投递到本渠道。</p>
 */
public interface NotificationChannel {

    ChannelType getType();

    boolean isEnabled();

    default Set<NotificationEvent.Type> supportedEvents() {
        return Set.of(NotificationEvent.Type.values());
    }

    void send(NotificationEvent event);
}
