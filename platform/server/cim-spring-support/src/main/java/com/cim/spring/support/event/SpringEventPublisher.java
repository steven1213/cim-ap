package com.cim.spring.support.event;

import com.cim.core.event.DomainEvent;
import com.cim.core.port.EventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 领域事件发布端口默认实现：桥接 Spring {@link ApplicationEventPublisher}（进程内）。
 *
 * <p>由 {@code SpringSupportAutoConfiguration} 以 {@code @ConditionalOnMissingBean} 注册；
 * {@code cim-mq-starter} 可提供带发件箱中继的增强实现（见 README §13）。</p>
 */
@RequiredArgsConstructor
public class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher delegate;

    @Override
    public void publish(DomainEvent event) {
        delegate.publishEvent(event);
    }
}
