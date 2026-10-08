package com.cim.cache.cluster;

import com.cim.cache.RedisTestSupport;
import com.cim.cache.guard.LocalLockProvider;
import com.cim.cache.guard.MutexRebuild;
import com.cim.cache.guard.NullValueGuard;
import com.cim.cache.guard.TtlJitter;
import com.cim.cache.multi.LocalCaffeineCache;
import com.cim.cache.multi.MultiLevelCache;
import com.cim.cache.multi.RedisCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨节点缓存失效真集成测试（需 Redis）：验证经 pub/sub 让对端清除本地 L1，实现多实例最终一致。
 *
 * <p>关键点：对端 L1 持有旧值时，若不处理失效事件会一直返回旧值；事件处理后方才重新回源。
 * 因此「回源次数增加」即可证明确实收到了广播并清了 L1。</p>
 */
class CacheInvalidationClusterTest {

    private static LettuceConnectionFactory factory;
    private static RedisTemplate<String, Object> template;
    private static ObjectMapper om;
    private static String prefix;

    @BeforeAll
    static void setUp() {
        Assumptions.assumeTrue(RedisTestSupport.reachable(),
                "Redis 不可达（" + RedisTestSupport.HOST + ":" + RedisTestSupport.PORT + "），跳过真集成测试");
        factory = RedisTestSupport.newConnectionFactory();
        template = RedisTestSupport.newRedisTemplate(factory);
        om = RedisTestSupport.newObjectMapper();
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

    private MultiLevelCache node() {
        return new MultiLevelCache(new LocalCaffeineCache(100, 60), new RedisCache(template, prefix),
                new NullValueGuard(true, 60), new TtlJitter(0, 0),
                new MutexRebuild(new LocalLockProvider(), 30),
                60, 300, om, null);
    }

    /** 订阅端逻辑单测：直接投递一条消息，验证对端 L1 被清、随后重新回源。 */
    @Test
    void subscriberClearsPeerLocalCache() throws Exception {
        MultiLevelCache peer = node();
        AtomicInteger calls = new AtomicInteger();
        peer.get("equipment:1", String.class, () -> {
            calls.incrementAndGet();
            return "v1";
        });
        assertTrue(calls.get() == 1);

        RedisInvalidationSubscriber subscriber = new RedisInvalidationSubscriber(peer, om);
        byte[] body = om.writeValueAsBytes(new CacheInvalidationEvent("equipment", "equipment:1", false));
        subscriber.onMessage(new DefaultMessage(
                CacheInvalidationBroadcaster.CHANNEL.getBytes(StandardCharsets.UTF_8), body), null);

        // 失效后再次读取应重新回源
        peer.get("equipment:1", String.class, () -> {
            calls.incrementAndGet();
            return "v1";
        });
        assertTrue(calls.get() == 2, "收到失效事件后应重新回源，实际=" + calls.get());
    }

    /** 端到端：真实 Redis pub/sub，容器订阅 + 广播，验证最终一致。 */
    @Test
    void endToEndPubSubInvalidatesPeer() throws Exception {
        MultiLevelCache peer = node();
        AtomicInteger calls = new AtomicInteger();
        peer.get("equipment:2", String.class, () -> {
            calls.incrementAndGet();
            return "v2";
        });
        assertTrue(calls.get() == 1);

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(new RedisInvalidationSubscriber(peer, om),
                new ChannelTopic(CacheInvalidationBroadcaster.CHANNEL));
        container.afterPropertiesSet();
        container.start();
        try {
            Thread.sleep(300); // 等订阅生效
            StringRedisTemplate srt = RedisTestSupport.newStringRedisTemplate(factory);
            srt.convertAndSend(CacheInvalidationBroadcaster.CHANNEL,
                    om.writeValueAsString(new CacheInvalidationEvent("equipment", "equipment:2", false)));

            long deadline = System.currentTimeMillis() + 4000;
            boolean reloaded = false;
            while (System.currentTimeMillis() < deadline) {
                peer.get("equipment:2", String.class, () -> {
                    calls.incrementAndGet();
                    return "v2";
                });
                if (calls.get() >= 2) {
                    reloaded = true;
                    break;
                }
                Thread.sleep(50);
            }
            assertTrue(reloaded, "端到端 pub/sub 应最终清除对端 L1 并触发回源，实际回源次数=" + calls.get());
        } finally {
            container.stop();
            container.destroy();
        }
    }
}
