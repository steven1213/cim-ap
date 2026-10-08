package com.cim.mq.demo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 演示用的业务负载：订单已创建事件。
 *
 * <p>这是 {@code IntegrationEvent.payload} 里承载的「线缆格式」JSON 对应的业务对象，
 * 由生产方序列化进信封、消费方反序列化出来做业务处理（此处用扣减库存举例）。</p>
 */
public class OrderCreatedEvent {

    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private List<OrderItem> items;

    public OrderCreatedEvent() {
    }

    public OrderCreatedEvent(String orderId, String customerId, BigDecimal amount, List<OrderItem> items) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.items = items;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public static class OrderItem {
        private String sku;
        private int qty;

        public OrderItem() {
        }

        public OrderItem(String sku, int qty) {
            this.sku = sku;
            this.qty = qty;
        }

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getQty() {
            return qty;
        }

        public void setQty(int qty) {
            this.qty = qty;
        }
    }
}
