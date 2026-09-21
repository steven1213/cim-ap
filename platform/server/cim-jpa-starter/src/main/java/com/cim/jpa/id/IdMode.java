package com.cim.jpa.id;

/**
 * 应用层主键生成策略（见 README §3）。
 */
public enum IdMode {

    /** 雪花算法（时间有序、数值紧凑）；时钟回拨时降级 {@link #UUIDV7}。 */
    SNOWFLAKE,

    /** UUIDv7（时间前缀 + 随机，索引局部性接近雪花，可读性更好）。 */
    UUIDV7
}
