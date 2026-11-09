package com.cim.notify.fanout;

import com.cim.notify.api.NotifyEvent;

/**
 * SSE 扇出 SPI（中立核心 + 双实现，对齐 auth/cache/mq 范式）。
 *
 * <ul>
 *   <li>{@code LocalSseFanout}：单实例 / dev，纯内存本地投递，零额外基础设施。</li>
 *   <li>{@code RedisSseFanout}：多实例，经 Redis pub/sub 按 {@code appCode} 命名空间扇出。</li>
 * </ul>
 */
public interface SseFanout {
    void fanout(NotifyEvent event);
}
