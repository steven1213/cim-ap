package com.cim.cache.guard;

import com.cim.cache.multi.CachedValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NullValueGuardTest {

    @Test
    void wrapsRealValue() {
        NullValueGuard guard = new NullValueGuard(true, 60);
        CachedValue cv = guard.wrap("hello");
        assertFalse(cv.isNullValue());
        assertEquals("hello", cv.getValue());
    }

    @Test
    void wrapsNullAsSentinelWhenEnabled() {
        NullValueGuard guard = new NullValueGuard(true, 60);
        CachedValue cv = guard.wrap(null);
        assertTrue(cv.isNullValue());
    }

    @Test
    void nullNotWrappedWhenDisabled() {
        NullValueGuard guard = new NullValueGuard(false, 60);
        CachedValue cv = guard.wrap(null);
        // 守卫关闭时仅做普通封装：是真实值（非占位），且取值为 null
        assertFalse(cv.isNullValue());
        assertTrue(cv.isReal());
        assertNull(cv.getValue());
    }
}
