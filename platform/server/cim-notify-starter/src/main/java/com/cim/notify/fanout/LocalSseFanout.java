package com.cim.notify.fanout;

import com.cim.notify.api.NotifyEvent;
import com.cim.notify.transport.SseRegistry;

/**
 * 本地扇出（单实例 / dev 兜底）：直接在本 Pod 内投递，不经过 Redis。
 */
public class LocalSseFanout implements SseFanout {

    private final SseRegistry registry;

    public LocalSseFanout(SseRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void fanout(NotifyEvent event) {
        registry.deliverLocal(event);
    }
}
