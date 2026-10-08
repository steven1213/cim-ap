package com.cim.mq;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.demo.InventoryListener;
import com.cim.mq.demo.OrderCreatedEvent;
import com.cim.mq.demo.OrderService;
import com.cim.mq.idempotent.IdempotencyStore;
import com.cim.mq.outbox.OutboxStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RabbitMQ 真集成测试（连 Docker 中的 rabbitmq: localhost:5672 / admin / admin123 / vhost=/）。
 *
 * <p>发件箱落内存 H2（与业务同事务），消息经真实 RabbitMQ broker 投递，消费侧由框架包裹
 * 幂等/重试/死信。验证 design.md §2.6 的两大保证：
 *  1) 不丢：下单 → outbox → 中继 → RabbitMQ → 真实消费者，且 outbox 投递后被清。
 *  2) 不重：同一 eventId 被 broker 重复投递时，业务方法仅执行一次。</p>
 */
@SpringBootTest(classes = RabbitMqIntegrationTest.TestApp.class, properties = {
        "cim.mq.enabled=true",
        "cim.mq.type=rabbit",
        "cim.mq.default-topic=order-events",
        "cim.mq.rabbit.addresses=localhost:5672",
        "cim.mq.rabbit.username=admin",
        "cim.mq.rabbit.password=admin123",
        "cim.mq.rabbit.virtual-host=/",
        "cim.mq.rabbit.exchange=cim-mq-exchange",
        "cim.mq.outbox.enabled=true",
        "cim.mq.outbox.relay-enabled=true",
        "cim.mq.outbox.interval-ms=500",
        "cim.mq.outbox.init-schema=true",
        "cim.mq.outbox.table=sys_outbox",
        "cim.mq.idempotent.store=memory",
        "cim.mq.idempotent.ttl-sec=86400",
        "spring.datasource.url=jdbc:h2:mem:cimrabbit;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "com.cim.spring.support.config.SpringSupportAutoConfiguration"
})
class RabbitMqIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Autowired
    OrderService orderService;
    @Autowired
    InventoryListener listener;
    @Autowired
    com.cim.mq.core.MqTemplate mqTemplate;
    @Autowired
    OutboxStore outboxStore;
    @Autowired
    IdempotencyStore idempotency;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("不丢：下单后经发件箱中继，消息通过真实 RabbitMQ 被消费，且 outbox 已清空")
    void noLoss() throws Exception {
        listener.reset(3);

        String o1 = orderService.placeOrder("C1", new BigDecimal("100.00"));
        String o2 = orderService.placeOrder("C2", new BigDecimal("200.00"));
        String o3 = orderService.placeOrder("C3", new BigDecimal("300.00"));

        listener.await(Duration.ofSeconds(30));

        // 1) 三条消息都被真实消费者处理（经真实 RabbitMQ）
        assertEquals(3, listener.handledCount(), "应收到 3 条订单事件");

        List<String> receivedIds = listener.getReceived().stream()
                .map(OrderCreatedEvent::getOrderId).collect(Collectors.toList());
        assertTrue(receivedIds.containsAll(List.of(o1, o2, o3)), "应包含全部订单号: " + receivedIds);

        // 2) 发件箱中已无 PENDING（证明中继成功投递并清理）；轮询等待可能的异步清理
        List<com.cim.mq.outbox.OutboxRecord> remaining = outboxStore.claimBatch(100);
        for (int i = 0; i < 50 && !remaining.isEmpty(); i++) {
            Thread.sleep(100);
            remaining = outboxStore.claimBatch(100);
        }
        if (!remaining.isEmpty()) {
            String detail = remaining.stream()
                    .map(r -> r.id() + ":" + r.status())
                    .collect(Collectors.joining(", "));
            System.out.println("[diagnostic] outbox 残留: " + detail);
        }
        assertTrue(remaining.isEmpty(), "outbox 应已清空, 残留=" + remaining.stream().map(com.cim.mq.outbox.OutboxRecord::id).collect(Collectors.joining(",")));
    }

    @Test
    @DisplayName("不重：broker 重复投递同一 eventId，业务方法仅执行一次")
    void noDuplicate() throws Exception {
        listener.reset(1);

        String o1 = orderService.placeOrder("C1", new BigDecimal("100.00"));
        listener.await(Duration.ofSeconds(30));
        assertEquals(1, listener.handledCount(), "首次应处理 1 次");

        // 确保幂等标记已生效（handler 返回后框架标记 processed）
        assertTrue(idempotency.isProcessed(o1), "eventId 应已被标记为已处理");

        // 模拟 broker 因网络抖动重复投递同一 eventId
        IntegrationEvent dup = IntegrationEvent.builder()
                .id(o1)
                .topic("order-created")
                .type("OrderCreated")
                .aggregateType("Order")
                .aggregateId(o1)
                .payloadType(OrderCreatedEvent.class.getName())
                .payload(objectMapper.writeValueAsString(
                        new OrderCreatedEvent(o1, "C1", new BigDecimal("100.00"), List.of())))
                .build();
        mqTemplate.send(dup);

        Thread.sleep(3000); // 等待可能的重复处理

        // 关键断言：重复投递不会让业务方法再执行一次
        assertEquals(1, listener.handledCount(), "幂等去重后不应重复处理");
        assertEquals(1, listener.getReceived().size());
    }
}
