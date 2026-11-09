package com.cim.notify.integration;

import com.cim.notify.api.NotifyEnvelope;
import com.cim.notify.api.NotifyEvent;
import com.cim.notify.api.NotifyPublisher;
import com.cim.notify.config.NotifyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.TestPropertySource;

import java.io.BufferedReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Redis 扇出端到端验证：开启 cluster，经真实 Redis pub/sub 完成
 * 发布 → 订阅者回投本 Pod 连接 的整链路。证明跨实例扇出在真实 Redis 下可用。
 *
 * <p>与 {@code NotifyEndToEndTest}（关 cluster，走 LocalSseFanout 免 Redis）互补：
 * 本测试需要可达的 Redis（默认 127.0.0.1:6379）。</p>
 */
// Redis 不可达时整类跳过：notifyFanoutContainer 在上下文启动阶段就会连 Redis，
// 方法内的 assumeTrue 来不及生效（会先抛 ApplicationContextException 把构建判红）。
@EnabledIf("redisReachable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NotifyRedisFanoutTest.Config.class)
@TestPropertySource(properties = {
        "cim.notify.enabled=true",
        "cim.notify.app-code=test-ap",
        "cim.notify.cluster.enabled=true",
        "cim.notify.dev-accept-query-identity=true",
        "cim.notify.heartbeat=3600s",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.timeout=3000"
})
class NotifyRedisFanoutTest {

    /** 127.0.0.1:6379 是否可达（供 {@link EnabledIf} 在加载上下文前判定）。 */
    static boolean redisReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6379), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Config {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private NotifyPublisher notifyPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisMessageListenerContainer notifyFanoutContainer;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void publishRoutedThroughRedisFanoutToSubscriber() throws Exception {
        // 先确认 Redis 可达；不可达则整测试跳过（避免 Redis-less CI 硬失败）
        String pong = null;
        try {
            pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
        } catch (Exception ignored) {
        }
        assumeTrue(pong != null, "Redis 不可达（127.0.0.1:6379），跳过 Redis 扇出测试");
        // 等订阅者挂上频道，避免发布早于订阅导致消息丢失
        boolean subscribed = false;
        for (int i = 0; i < 50; i++) { // 最多等 5s 让 RedisMessageListenerContainer 完成订阅
            if (notifyFanoutContainer.isListening()) {
                subscribed = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(subscribed, "RedisMessageListenerContainer 未在 5s 内完成频道订阅");

        String tenant = "t1";
        String userId = "dev";
        String eventType = "HELLO_R";
        String payload = "{\"taskId\":7,\"title\":\"Redis 扇出\"}";

        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch got = new CountDownLatch(1);
        ExecutorService reader = Executors.newSingleThreadExecutor();

        reader.submit(() -> {
            String url = "http://localhost:" + port + "/sys/notify/stream?userId=" + userId + "&tenantId=" + tenant;
            restTemplate.execute(url, HttpMethod.GET, null, (ClientHttpResponse resp) -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                    StringBuilder buf = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            buf.append(line.substring(5).strip());
                        } else if (line.isEmpty()) {
                            if (buf.length() > 0) {
                                received.set(buf.toString());
                                got.countDown();
                                return null;
                            }
                            buf.setLength(0);
                        }
                    }
                }
                return null;
            });
        });

        Thread.sleep(300); // 等 SSE 连接注册进 registry

        notifyPublisher.publish(new NotifyEvent(tenant, NotifyEvent.TargetType.USER,
                List.of(userId), "test-ap", eventType, payload));

        assertTrue(got.await(5, TimeUnit.SECONDS), "未在 5s 内经 Redis 扇出收到 SSE 推送");

        NotifyEnvelope env = objectMapper.readValue(received.get(), NotifyEnvelope.class);
        assertNotNull(env.getId());
        assertEquals("test-ap", env.getAppCode());
        assertEquals(eventType, env.getType());
        assertEquals(payload, env.getPayload()); // 双编码契约

        reader.shutdownNow();
    }
}
