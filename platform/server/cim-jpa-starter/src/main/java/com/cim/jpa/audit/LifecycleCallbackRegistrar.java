package com.cim.jpa.audit;

import com.cim.core.event.EntityLifecycleCallback;
import com.cim.core.event.EntityLifecycleCallbacks;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;

/**
 * 把 Spring 管理的 {@link EntityLifecycleCallback} 注册进 {@code cim-core} 的静态持有者，
 * 使 JPA {@code @PrePersist}/{@code @PreUpdate} 回调（定义在基类，零 Spring 依赖）
 * 能走到基础设施实现。
 */
@RequiredArgsConstructor
public class LifecycleCallbackRegistrar implements InitializingBean {

    private final EntityLifecycleCallback callback;

    @Override
    public void afterPropertiesSet() {
        EntityLifecycleCallbacks.register(callback);
    }
}
