package com.cim.mq.idempotent;

import java.time.Duration;

/**
 * 幂等去重存储契约（供应商中立）。
 *
 * <p>用于消费侧「精确一次」：以 {@code eventId}（或 {@code aggregateId+version}）为键，
 * 处理前先查、处理后标记，重复投递的消息直接跳过（README §13/§16）。</p>
 */
public interface IdempotencyStore {

    /**
     * 是否已被处理过。
     */
    boolean isProcessed(String key);

    /**
     * 标记为已处理（带 TTL，过期后允许重新处理以自愈）。
     */
    void markProcessed(String key, Duration ttl);

    /**
     * 原子「检查并标记」：首次返回 true（可处理），重复返回 false（应跳过）。
     */
    boolean tryMark(String key, Duration ttl);

    /** 清除（测试 / 补偿）。 */
    void clear(String key);
}
