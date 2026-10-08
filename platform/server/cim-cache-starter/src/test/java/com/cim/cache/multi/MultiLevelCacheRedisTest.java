package com.cim.cache.multi;

import com.cim.cache.RedisTestSupport;
import com.cim.cache.guard.LocalLockProvider;
import com.cim.cache.guard.MutexRebuild;
import com.cim.cache.guard.NullValueGuard;
import com.cim.cache.guard.RedisLockProvider;
import com.cim.cache.guard.TtlJitter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真集成测试：连接真实 Redis（默认 127.0.0.1:6379，本机 Docker），验证多级缓存的 L2 行为。
 *
 * <p>覆盖：L1→L2 回填与跨实例共享、失效清两级、Redis 分布式锁互斥、Redis 不可达时降级。
 * Redis 不可达时整体跳过（{@link Assumptions}），保证无 Redis 的 CI 仍可跑。</p>
 */
class MultiLevelCacheRedisTest {

    private static LettuceConnectionFactory factory;
    private static RedisTemplate<String, Object> template;
    private static String prefix;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(RedisTestSupport.reachable(),
                "Redis 不可达（" + RedisTestSupport.HOST + ":" + RedisTestSupport.PORT + "），跳过真集成测试");
        factory = RedisTestSupport.newConnectionFactory();
        template = RedisTestSupport.newRedisTemplate(factory);
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void uniquePrefix() {
        prefix = "cimtest:" + UUID.randomUUID() + ":";
    }

    /** 构建一个「独立实例」：各自独立 L1，但共享同一 L2（同一 Redis 前缀）。 */
    private MultiLevelCache node() {
        LocalCaffeineCache l1 = new LocalCaffeineCache(100, 60);
        RedisCache l2 = new RedisCache(template, prefix);
        return new MultiLevelCache(l1, l2,
                new NullValueGuard(true, 60),
                new TtlJitter(0, 0),
                new MutexRebuild(new LocalLockProvider(), 30),
                60, 300, RedisTestSupport.newObjectMapper(), null);
    }

    @Test
    void l2BackfillsAcrossInstances() {
        MultiLevelCache node1 = node();
        MultiLevelCache node2 = node();
        AtomicInteger c1 = new AtomicInteger();
        AtomicInteger c2 = new AtomicInteger();

        assertEquals("v1", node1.get("equipment:1", String.class, () -> {
            c1.incrementAndGet();
            return "v1";
        }));
        // node2 的 L1 为空 → 命中 L2 → 回填 L1 且不回源
        assertEquals("v1", node2.get("equipment:1", String.class, () -> {
            c2.incrementAndGet();
            return "v1";
        }));
        assertEquals(1, c1.get(), "node1 只回源一次");
        assertEquals(0, c2.get(), "node2 应命中 L2，不回源");
    }

    @Test
    void evictClearsL2SoPeerReloads() {
        MultiLevelCache node1 = node();
        MultiLevelCache node2 = node();
        AtomicInteger c1 = new AtomicInteger();
        AtomicInteger c2 = new AtomicInteger();

        node1.get("equipment:9", String.class, () -> {
            c1.incrementAndGet();
            return "v9";
        });
        node1.evict("equipment:9"); // 清 L1 + L2
        node2.get("equipment:9", String.class, () -> { // node2 L1 空、L2 已删 → 回源
            c2.incrementAndGet();
            return "v9";
        });
        assertEquals(1, c1.get());
        assertEquals(1, c2.get(), "失效后对端应重新回源");
    }

    @Test
    void redisLockProviderMutualExclusion() {
        RedisLockProvider lockA = new RedisLockProvider(template, prefix + "lock:");
        RedisLockProvider lockB = new RedisLockProvider(template, prefix + "lock:");
        assertTrue(lockA.tryLock("k", 30), "第一个应抢到锁");
        assertFalse(lockB.tryLock("k", 30), "第二个应被互斥");
        lockA.unlock("k");
        assertTrue(lockB.tryLock("k", 30), "释放后应可再抢");
    }

    @Test
    void degradesGracefullyWhenRedisUnreachable() {
        // 指向一个无监听端口，验证 L2 异常被吞没、整体仍可用（走 loader）
        LettuceConnectionFactory dead = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", 6399));
        dead.afterPropertiesSet();
        try {
            RedisTemplate<String, Object> deadTemplate = RedisTestSupport.newRedisTemplate(dead);
            RedisCache deadL2 = new RedisCache(deadTemplate, prefix);
            MultiLevelCache cache = new MultiLevelCache(new LocalCaffeineCache(100, 60), deadL2,
                    new NullValueGuard(true, 60), new TtlJitter(0, 0),
                    new MutexRebuild(new LocalLockProvider(), 30),
                    60, 300, RedisTestSupport.newObjectMapper(), null);

            AtomicInteger calls = new AtomicInteger();
            assertEquals("fallback", cache.get("equipment:x", String.class, () -> {
                calls.incrementAndGet();
                return "fallback";
            }));
            // 第二次由 L1 兜住（L2 不可用不影响 L1 命中）
            assertEquals("fallback", cache.get("equipment:x", String.class, () -> {
                calls.incrementAndGet();
                return "fallback";
            }));
            assertEquals(1, calls.get(), "L2 不可用时仍应能靠 L1 命中，只回源一次");
        } finally {
            dead.destroy();
        }
    }
}
