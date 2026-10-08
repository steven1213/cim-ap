package com.cim.mq.demo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.cim.mq.OutboxIntegrationEventPublisher;
import com.cim.mq.core.IntegrationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示：下单后在同一数据库事务里写业务表 + 写发件箱（outbox）。
 *
 * <p>关键保证（设计 §12 / §2.6）：{@code INSERT t_order} 与 {@code INSERT sys_outbox}
 * 处于<b>同一事务</b>。二者要么都提交、要么都回滚——绝不会出现「订单落库但事件丢失」，
 * 也不会出现「事件发出但订单没保存」。提交后由 {@code OutboxRelay} 轮询投递到 MQ，
 * 配合消费侧幂等实现「不丢、不重」。</p>
 */
@Service
public class OrderService {

    private final JdbcTemplate jdbc;
    private final OutboxIntegrationEventPublisher outbox;
    private final ObjectMapper objectMapper;

    public OrderService(JdbcTemplate jdbc, OutboxIntegrationEventPublisher outbox, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    /** 演示用：建一张极简订单表，证明 outbox 与业务写在同一事务。 */
    @jakarta.annotation.PostConstruct
    public void initSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_order ("
                + "id VARCHAR(64) PRIMARY KEY, customer VARCHAR(128), amount DECIMAL(12,2))");
    }

    /**
     * 下单：业务写 + 集成事件发布，二者同事务。
     *
     * @return 订单号（同时作为集成事件 id，天然作为幂等去重键）
     */
    @Transactional
    public String placeOrder(String customerId, BigDecimal amount) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO t_order(id, customer, amount) VALUES (?,?,?)",
                orderId, customerId, amount);

        OrderCreatedEvent payload = new OrderCreatedEvent(orderId, customerId, amount, List.of());
        IntegrationEvent event = IntegrationEvent.builder()
                .id(orderId)                       // eventId == orderId，天然去重键
                .topic("order-created")
                .type("OrderCreated")
                .aggregateType("Order")
                .aggregateId(orderId)
                .payloadType(OrderCreatedEvent.class.getName())
                .payload(json(payload))
                .build();

        outbox.publish(event);                    // 落到 sys_outbox（同一事务）
        return orderId;
    }

    private String json(OrderCreatedEvent e) {
        try {
            return objectMapper.writeValueAsString(e);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化订单事件失败", ex);
        }
    }
}
