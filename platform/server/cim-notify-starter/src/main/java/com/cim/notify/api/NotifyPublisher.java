package com.cim.notify.api;

import java.util.List;

/**
 * 实时推送发布门面（业务 ap 唯一依赖面）。
 *
 * <p>业务 ap 调用 {@link #publish(NotifyEvent)} 触发实时推送；扇出（本地 / Redis）
 * 由 starter 按配置选择，业务无感知。</p>
 */
public interface NotifyPublisher {

    void publish(NotifyEvent event);

    /** 便捷方法：定向推送给单个用户。 */
    default void publishToUser(String tenantId, String userId, String appCode, String type, String payload) {
        publish(new NotifyEvent(tenantId, NotifyEvent.TargetType.USER, List.of(userId), appCode, type, payload));
    }
}
