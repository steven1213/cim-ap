package com.cim.core.port;

import com.cim.core.event.DomainEvent;

/**
 * 领域事件发布端口（进程内）。
 *
 * <p>实现由 {@code cim-mq-starter}（或 {@code cim-spring-support} 的默认 {@code ApplicationEvent}
 * 实现）提供；跨服务集成事件另经发件箱中继（见 README §12 / §13）。</p>
 */
public interface EventPublisher {

    /**
     * 发布领域事件。
     *
     * @param event 领域事件
     */
    void publish(DomainEvent event);
}
