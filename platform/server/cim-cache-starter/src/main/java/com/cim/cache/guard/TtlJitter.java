package com.cim.cache.guard;

import java.util.concurrent.ThreadLocalRandom;

/**
 * TTL 随机抖动（防雪崩）：对同一批设置了相近 TTL 的 key，加 ±比例 的随机偏移，
 * 避免在同一时刻集体过期、瞬时回源压垮 DB。抖动上限由 {@code maxJitter} 封顶。
 */
public final class TtlJitter {

    private final double percent;
    private final long maxJitter;

    public TtlJitter(double percent, long maxJitter) {
        this.percent = percent;
        this.maxJitter = maxJitter;
    }

    /**
     * 在 {@code baseSeconds} 基础上施加随机抖动。
     *
     * @return 抖动后的 TTL（秒），至少 1 秒；入参 <=0 或不启用抖动时原样返回。
     */
    public long apply(long baseSeconds) {
        if (baseSeconds <= 0 || percent <= 0) {
            return baseSeconds;
        }
        long jitter = (long) (baseSeconds * percent);
        jitter = Math.min(jitter, maxJitter);
        if (jitter <= 0) {
            return baseSeconds;
        }
        long delta = ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        return Math.max(1, baseSeconds + delta);
    }
}
