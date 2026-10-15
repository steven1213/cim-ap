package com.cim.rms.server.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 站内信默认渠道：通知落库即达（前端轮询拉取），无外发动作。
 * 将来的邮件/企微/消息队列渠道与本类并列，互不影响。
 */
@Slf4j
@Component
public class InAppNotifyChannel implements NotifyChannel {

    @Override
    public String name() {
        return "IN_APP";
    }

    @Override
    public void deliver(RmsNotification notification) {
        log.debug("[notify] 站内信已落库 id={} type={} recipient={} audience={}",
                notification.getId(), notification.getType(), notification.getRecipient(),
                notification.getAudience());
    }
}
