package com.cim.jpa.id;

/**
 * 雪花算法实现（64 位：1 符号 + 41 时间戳 + 5 数据中心 + 5 机器 + 12 序列）。
 *
 * <p>时间有序、数值紧凑，利于索引局部性；workerId/datacenterId 应由部署平台下发并保证
 * 同数据中心唯一（见 README §3.1）。时钟回拨超过容忍阈值时抛
 * {@link ClockBackwardsException}，由上层降级。</p>
 */
public class Snowflake {

    /** 起始纪元：2024-01-01T00:00:00Z。 */
    private static final long EPOCH = 1704067200000L;

    private static final long WORKER_BITS = 5L;
    private static final long DATACENTER_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

    /** 允许的时钟回拨容忍（毫秒），超过则抛异常降级。 */
    private static final long MAX_BACKWARD_TOLERANCE = 5L;

    private final long workerId;
    private final long datacenterId;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public Snowflake(long workerId, long datacenterId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be in [0, " + MAX_WORKER_ID + "]");
        }
        if (datacenterId < 0 || datacenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("datacenterId must be in [0, " + MAX_DATACENTER_ID + "]");
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    /**
     * 生成下一个 ID。
     *
     * @throws ClockBackwardsException 时钟回拨超过容忍阈值
     */
    public synchronized long nextId() {
        long timestamp = currentMillis();
        if (timestamp < lastTimestamp) {
            long backward = lastTimestamp - timestamp;
            if (backward > MAX_BACKWARD_TOLERANCE) {
                throw new ClockBackwardsException(backward);
            }
            // 小范围回拨：等待追平
            timestamp = waitUntil(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntil(lastTimestamp + 1);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    /** 生成字符串 ID。 */
    public String nextIdStr() {
        return Long.toString(nextId());
    }

    private long waitUntil(long target) {
        long ts = currentMillis();
        while (ts < target) {
            ts = currentMillis();
        }
        return ts;
    }

    private long currentMillis() {
        return System.currentTimeMillis();
    }
}
