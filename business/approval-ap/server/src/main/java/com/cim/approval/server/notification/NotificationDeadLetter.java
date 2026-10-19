package com.cim.approval.server.notification;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 通知死信：记录渠道投递失败的最近条目（生产应并入既有回调 Outbox 体系做重试 + 死信，见框架 G7）。
 *
 * <p>此处为轻量可观测实现，保留最近 1000 条失败记录供排查。</p>
 */
@Component
public class NotificationDeadLetter {

    private final LinkedBlockingQueue<Failed> q = new LinkedBlockingQueue<>(1000);

    public void record(NotificationEvent event, ChannelType channel, String error) {
        q.offer(new Failed(Instant.now(), channel, event.type(), event.recipients(), error));
    }

    public List<Failed> recent() {
        return new ArrayList<>(q);
    }

    public record Failed(Instant time, ChannelType channel, NotificationEvent.Type type,
                         List<String> recipients, String error) {
    }
}
