package com.cim.jpa.id;

import com.cim.core.port.IdGenerator;

/**
 * UUIDv7 主键生成器（{@code cim.jpa.id.mode=UUIDV7} 时启用）。
 */
public class UuidV7IdGenerator implements IdGenerator {

    @Override
    public String nextId() {
        return UuidV7.randomNoDash();
    }
}
