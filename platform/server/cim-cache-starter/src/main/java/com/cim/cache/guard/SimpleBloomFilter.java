package com.cim.cache.guard;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * 极简可自维护的 BloomFilter（基于 {@link BitSet} + 双哈希派生 k 个位），用于防穿透增强。
 *
 * <p>不引入额外依赖（如 Guava），满足 starter 轻量诉求。语义保证：{@link #mightContain} 返回
 * {@code false} 时，元素「一定不在集合中」（无假阴性）；返回 {@code true} 时仅在极小概率下为假阳性。</p>
 *
 * <p>典型用法：业务侧将「可能存在有效数据的 key 集合」预先灌入（启动时按 ID 全量加载，或在写入实体时
 * {@link #put}），缓存守卫据此在 key 确定不存在时直接短路、不查 DB。</p>
 */
public class SimpleBloomFilter {

    private final BitSet bits;
    private final int m;
    private final int k;

    public SimpleBloomFilter(int bitSize, int hashFunctions) {
        this.m = Math.max(1, bitSize);
        this.k = Math.max(1, hashFunctions);
        this.bits = new BitSet(this.m);
    }

    public void put(String value) {
        for (int idx : hashes(value)) {
            bits.set(idx);
        }
    }

    public boolean mightContain(String value) {
        for (int idx : hashes(value)) {
            if (!bits.get(idx)) {
                return false;
            }
        }
        return true;
    }

    private int[] hashes(String value) {
        int h1 = fnv1a(value);
        int h2 = djb2(value);
        int[] out = new int[k];
        for (int i = 0; i < k; i++) {
            int h = h1 + i * h2;
            h %= m;
            if (h < 0) {
                h += m;
            }
            out[i] = h;
        }
        return out;
    }

    private static int fnv1a(String s) {
        int h = 0x811c9dc5;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x01000193;
        }
        return h;
    }

    private static int djb2(String s) {
        int h = 5381;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h = ((h << 5) + h) + (b & 0xff);
        }
        return h;
    }
}
