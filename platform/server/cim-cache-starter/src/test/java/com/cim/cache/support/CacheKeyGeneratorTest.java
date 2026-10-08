package com.cim.cache.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheKeyGeneratorTest {

    private final CacheKeyGenerator gen = new CacheKeyGenerator("cim");

    @Test
    void spelByIndex() {
        assertEquals("cim:equipment:1", gen.generate("equipment", "#p0", new Object[]{"1"}));
    }

    @Test
    void spelByProperty() {
        record Item(String id) {
        }
        assertEquals("cim:item:abc", gen.generate("item", "#p0.id", new Object[]{new Item("abc")}));
    }

    @Test
    void spelByArgsArray() {
        assertEquals("cim:item:xyz", gen.generate("item", "#args[0].id", new Object[]{new ItemX("xyz")}));
    }

    @Test
    void blankKeyFallsBackToArgs() {
        String key = gen.generate("equipment", "", new Object[]{"1"});
        assertTrue(key.startsWith("cim:equipment:"));
        assertTrue(key.contains("1"));
    }

    @Test
    void noGlobalPrefix() {
        CacheKeyGenerator g = new CacheKeyGenerator("");
        assertEquals("equipment:1", g.generate("equipment", "#p0", new Object[]{"1"}));
    }

    record ItemX(String id) {
    }
}
