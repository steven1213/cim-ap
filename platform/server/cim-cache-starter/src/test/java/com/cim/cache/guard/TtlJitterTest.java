package com.cim.cache.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtlJitterTest {

    @Test
    void jitterWithinBounds() {
        TtlJitter jitter = new TtlJitter(0.1, 60);
        long base = 300;
        for (int i = 0; i < 100; i++) {
            long r = jitter.apply(base);
            assertTrue(r >= 270 && r <= 330, "jitter out of bounds: " + r);
        }
    }

    @Test
    void zeroBaseOrZeroPercentUnchanged() {
        TtlJitter jitter = new TtlJitter(0.1, 60);
        assertEquals(0, jitter.apply(0));
        TtlJitter noJitter = new TtlJitter(0, 60);
        assertEquals(300, noJitter.apply(300));
    }

    @Test
    void jitterCappedByMax() {
        TtlJitter jitter = new TtlJitter(1.0, 60); // 100% would be ±300 but capped at 60
        long r = jitter.apply(300);
        assertTrue(r >= 240 && r <= 360, "capped jitter out of bounds: " + r);
    }
}
