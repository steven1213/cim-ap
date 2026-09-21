package com.cim.core.event;

import java.time.LocalDateTime;

/**
 * 领域事件基类（进程内）。
 *
 * <p>与「集成事件」（跨服务，经发件箱 + MQ）区分：领域事件在同一应用进程内通过
 * {@link com.cim.core.port.EventPublisher} 发布，监听器可插拔、可异步（见 README §13）。</p>
 */
public interface DomainEvent {

    /** 事件发生时间。 */
    LocalDateTime occurredAt();

    /** 事件类型标识（用于路由与去重）。 */
    default String eventType() {
        return getClass().getSimpleName();
    }
}
