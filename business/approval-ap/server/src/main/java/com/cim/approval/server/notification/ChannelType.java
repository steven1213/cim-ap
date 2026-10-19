package com.cim.approval.server.notification;

/** 通知渠道类型。IN_APP 始终启用；WECOM 按配置启用；EMAIL 为后续扩展位。 */
public enum ChannelType {
    IN_APP,
    WECOM,
    EMAIL
}
