package com.cim.cache.guard;

import com.cim.cache.multi.CachedValue;

/**
 * 空值守卫（防穿透）：对「查不到结果」的 key 缓存一个短 TTL 占位符，使后续相同 key 的请求
 * 在占位 TTL 内直接命中缓存（返回 null），不再反复穿透到 DB。
 *
 * <p>注意：仅对「确实查无数据」的场景生效；业务异常不应被缓存。</p>
 */
public final class NullValueGuard {

    private final boolean enabled;
    private final long ttlSeconds;

    public NullValueGuard(boolean enabled, long ttlSeconds) {
        this.enabled = enabled;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 空值占位的 TTL（秒，宜短）。 */
    public long ttlSeconds() {
        return ttlSeconds;
    }

    /**
     * 将原始返回值包装为缓存信封：若值为 null 且守卫开启，返回空值占位；否则原样封装。
     */
    public CachedValue wrap(Object value) {
        if (value == null && enabled) {
            return CachedValue.nullSentinel();
        }
        return CachedValue.of(value);
    }
}
