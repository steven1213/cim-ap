package com.cim.notify.integration;

import com.cim.notify.api.NotifyEnvelope;
import com.cim.notify.api.NotifyEvent;
import com.cim.notify.api.NotifyPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.TestPropertySource;

import java.io.BufferedReader;
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

/**
 * 端到端验证：经 {@code /sys/notify/stream} 订阅 → NotifyPublisher 发布 →
 * 本地扇出（LocalSseFanout，无需 Redis）→ 连接收到信封。证明全套装配链路可用。
 *
 * <p>Redis 扇出（集群跨实例）依赖宿主 ap 的 StringRedisTemplate，本测试关闭 cluster，
 * 仅验证「单实例 + 本地兜底」链路；跨实例路径由 {@code RedisSseFanoutTest} 覆盖频道命名。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NotifyEndToEndTest.Config.class)
@TestPropertySource(properties = {
        "cim.notify.enabled=true",
        "cim.notify.app-code=test-ap",
        "cim.notify.cluster.enabled=false",
        "cim.notify.dev-accept-query-identity=true",
        "cim.notify.heartbeat=3600s",
        // 关闭 Redis 自动装配，避免本测试建立无用连接 / 触发 StringRedisTemplate Bean
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class NotifyEndToEndTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Config {
        /** 提供真实 MeterRegistry，避免 @MockBean 的 more() 返回 null 触发 TaskExecutor 指标绑定 NPE。 */
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private NotifyPublisher notifyPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void publishDeliveredToSubscriber_overLocalFanout() throws Exception {
        String tenant = "t1";
        String userId = "dev";
        String eventType = "HELLO";
        String payload = "{\"taskId\":42,\"title\":\"批次签核\"}";

        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch got = new CountDownLatch(1);
        ExecutorService reader = Executors.newSingleThreadExecutor();

        // 后台打开 SSE 流并读取首条 data
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

        // 等连接建立并注册到 registry（短暂让出，避免发布早于 subscribe 完成的竞态）
        Thread.sleep(500);

        notifyPublisher.publish(new NotifyEvent(tenant, NotifyEvent.TargetType.USER,
                List.of(userId), "test-ap", eventType, payload));

        assertTrue(got.await(5, TimeUnit.SECONDS), "未在 5s 内收到 SSE 推送");

        NotifyEnvelope env = objectMapper.readValue(received.get(), NotifyEnvelope.class);
        assertNotNull(env.getId());
        assertEquals("test-ap", env.getAppCode());
        assertEquals(eventType, env.getType());
        // payload 为 JSON 字符串（双编码契约）：前端须 JSON.parse 一次
        assertEquals(payload, env.getPayload());

        reader.shutdownNow();
    }
}
