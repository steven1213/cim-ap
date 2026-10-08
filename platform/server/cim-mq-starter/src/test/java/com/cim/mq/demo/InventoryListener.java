package com.cim.mq.demo;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

import com.cim.mq.core.IntegrationEvent;
import com.cim.mq.listener.MqListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 演示：库存服务消费「订单已创建」事件（对应 design.md 中「不丢、不重」的消费侧）。
 *
 * <p>标注 {@link MqListener} 后由框架自动注册到 MQ，并自动包裹：幂等去重 + 重试 + 死信。
 * 业务方法只关心「收到一次就处理一次」——重复投递由框架在 {@code ResilientMessageHandler}
 * 里靠 eventId 幂等拦截，不会进入本方法第二次。</p>
 */
@Component
public class InventoryListener {

    private final ObjectMapper objectMapper;
    private final List<OrderCreatedEvent> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger handled = new AtomicInteger(0);
    private volatile CountDownLatch latch = new CountDownLatch(0);

    public InventoryListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 测试用：重置计数并设定本批次期望收到的条数。 */
    public void reset(int expected) {
        received.clear();
        handled.set(0);
        latch = new CountDownLatch(expected);
    }

    public List<OrderCreatedEvent> getReceived() {
        return received;
    }

    public int handledCount() {
        return handled.get();
    }

    public void await(Duration timeout) throws InterruptedException {
        latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    @MqListener(topic = "order-created", group = "inventory")
    public void onOrderCreated(IntegrationEvent event) throws Exception {
        OrderCreatedEvent order = objectMapper.readValue(event.payload(), OrderCreatedEvent.class);
        // 真实业务：扣减库存、生成履约单等（此处仅记录）
        received.add(order);
        handled.incrementAndGet();
        latch.countDown();
    }
}
