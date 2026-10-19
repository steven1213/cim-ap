package com.cim.approval.sdk.dto;

/**
 * 业务摘要字段（G5）：卡片/列表/审计一眼可见的关键业务项。
 * 与审批平台服务端契约一致，详见 Integration-API.md §8。
 */
public record DisplayField(String key, String label, String value) {
}
