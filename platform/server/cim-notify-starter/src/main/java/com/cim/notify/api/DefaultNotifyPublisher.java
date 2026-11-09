package com.cim.notify.api;

import com.cim.notify.fanout.SseFanout;
import com.cim.notify.metrics.NotifyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * {@link NotifyPublisher} 默认实现：补全事件元信息 → 指标 → 扇出。
 */
public class DefaultNotifyPublisher implements NotifyPublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultNotifyPublisher.class);

    private final SseFanout fanout;
    private final NotifyMetrics metrics;

    public DefaultNotifyPublisher(SseFanout fanout, NotifyMetrics metrics) {
        this.fanout = fanout;
        this.metrics = metrics;
    }

    @Override
    public void publish(NotifyEvent event) {
        if (event.getId() == null) {
            event.setId(UUID.randomUUID().toString());
        }
        if (event.getTs() == 0) {
            event.setTs(System.currentTimeMillis());
        }
        String appCode = event.getAppCode() == null ? "?" : event.getAppCode();
        if (event.getAppCode() == null) {
            log.warn("[cim-notify] publish event without appCode: type={}", event.getType());
        }
        metrics.increment("notify.published", "appCode", appCode);
        fanout.fanout(event);
    }
}
