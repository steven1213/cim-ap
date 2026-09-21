package com.cim.jpa.id;

/**
 * 时钟回拨异常：雪花算法检测到机器时钟回拨时抛出，由 {@code SnowflakeIdGenerator}
 * 捕获并降级为 UUIDv7（见 README §3.1）。
 */
public class ClockBackwardsException extends RuntimeException {

    private final long backMillis;

    public ClockBackwardsException(long backMillis) {
        super("Clock moved backwards by " + backMillis + " ms");
        this.backMillis = backMillis;
    }

    /** 回拨毫秒数。 */
    public long getBackMillis() {
        return backMillis;
    }
}
