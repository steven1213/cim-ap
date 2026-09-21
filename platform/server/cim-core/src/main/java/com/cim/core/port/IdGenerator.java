package com.cim.core.port;

/**
 * 应用层「时间有序 ID」生成端口。
 *
 * <p>跨库统一主键策略：规避 Oracle IDENTITY 差异、利于索引局部性、支持分布式与分库分表，
 * 且与历史表 {@code history_id} 同源（见 README §3）。实现示例：雪花（时钟回拨降级 UUIDv7）、
 * UUIDv7。</p>
 */
@FunctionalInterface
public interface IdGenerator {

    /**
     * 生成一个时间有序 ID 字符串。
     *
     * @return 全局唯一、时间有序的 ID
     */
    String nextId();
}
