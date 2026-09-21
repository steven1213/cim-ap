package com.cim.jpa.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 雪花算法验收：唯一性与时间有序（对齐 plan.md M1 的 DoD）。
 */
class SnowflakeTest {

    @Test
    void generatesUniqueIds() {
        Snowflake snowflake = new Snowflake(1, 1);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(snowflake.nextId());
        }
        assertThat(ids).hasSize(10_000);
    }

    @Test
    void idsAreTimeOrdered() {
        Snowflake snowflake = new Snowflake(1, 1);
        long previous = snowflake.nextId();
        for (int i = 0; i < 1_000; i++) {
            long current = snowflake.nextId();
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
    }

    @Test
    void rejectsInvalidWorkerId() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Snowflake(32, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
