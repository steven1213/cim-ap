package com.cim.rms.server.notification;

/**
 * 通知投递渠道 SPI（W9）：站内信是内置默认渠道；将来接邮件 / 企业微信 /
 * cim-mq-starter 消息通道时，只须再加一个实现 Bean，发布侧零改动。
 *
 * <p>实现方约定：{@link #deliver} 内部<b>不得抛异常</b>——通知失败绝不能拖垮业务事务
 * （发布侧仍会兜底 try/catch，但实现方应自行容错）。</p>
 */
public interface NotifyChannel {

    /** 渠道名（日志/排查用）。 */
    String name();

    /**
     * 投递一条通知。站内信渠道为 no-op（落库即达）；外发渠道在此调用 SMTP/Webhook 等。
     */
    void deliver(RmsNotification notification);
}
