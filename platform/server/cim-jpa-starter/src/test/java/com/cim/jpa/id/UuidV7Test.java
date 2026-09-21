package com.cim.jpa.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UUIDv7 验收：版本位正确、唯一、时间前缀非递减。
 */
class UuidV7Test {

    @Test
    void hasVersion7AndVariantBits() {
        UUID uuid = UuidV7.random();
        assertThat(uuid.version()).isEqualTo(7);
        assertThat(uuid.variant()).isEqualTo(2); // IETF variant (10x)
    }

    @Test
    void uniqueAndTimePrefixNonDecreasing() {
        Set<String> ids = new HashSet<>();
        String previousPrefix = "000000000000";
        for (int i = 0; i < 5_000; i++) {
            String id = UuidV7.randomNoDash();
            ids.add(id);
            String prefix = id.substring(0, 12); // 48 位毫秒时间戳（前 12 个 hex）
            assertThat(prefix).isGreaterThanOrEqualTo(previousPrefix);
            previousPrefix = prefix;
        }
        assertThat(ids).hasSize(5_000);
    }
}
