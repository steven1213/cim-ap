package com.cim.jpa.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 生成器：48 位毫秒时间戳 + 版本 + 随机位，兼顾时间有序与全局唯一。
 *
 * <p>作为雪花算法的降级与可读性优先的替代方案（见 README §3.1）。</p>
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    /** 生成一个 UUIDv7（时间有序）。 */
    public static UUID random() {
        long timestamp = System.currentTimeMillis() & 0xFFFFFFFFFFFFL; // 48 位

        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);

        long msb = (timestamp << 16)
                | 0x7000L                                  // version 7
                | ((long) (rand[0] & 0x0F) << 8)
                | (rand[1] & 0xFFL);

        long lsb = 0L;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (rand[i] & 0xFFL);
        }
        lsb &= 0x3FFFFFFFFFFFFFFFL;                        // 清 variant 位
        lsb |= 0x8000000000000000L;                        // variant 10

        return new UUID(msb, lsb);
    }

    /** 生成字符串形式（去连字符）。 */
    public static String randomNoDash() {
        return random().toString().replace("-", "");
    }
}
