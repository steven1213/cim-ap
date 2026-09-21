package com.cim.jpa.id;

import com.cim.core.port.IdGenerator;
import lombok.extern.slf4j.Slf4j;

/**
 * 雪花主键生成器：正常走雪花，时钟回拨时降级 UUIDv7 并告警（见 README §3 / §3.1）。
 */
@Slf4j
public class SnowflakeIdGenerator implements IdGenerator {

    private final Snowflake snowflake;

    public SnowflakeIdGenerator(long workerId, long datacenterId) {
        this.snowflake = new Snowflake(workerId, datacenterId);
    }

    @Override
    public String nextId() {
        try {
            return snowflake.nextIdStr();
        } catch (ClockBackwardsException e) {
            log.error("[IdGenerator] clock moved backwards {} ms, fallback to UUIDv7", e.getBackMillis());
            return UuidV7.randomNoDash();
        }
    }
}
