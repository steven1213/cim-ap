package com.cim.approval.server.dto;

/**
 * 业务摘要字段（G5 闸门出口）：审批卡片/列表/审计中一眼可见的关键业务项。
 *
 * <p>业务系统在发起时挑出最该被一眼看到的字段，预渲染好展示值，平台原样渲染，不做二次解析。
 * 对应 {@code StartProcessRequest.displayFields}，持久化为流程变量 {@code displayFieldsJson}。</p>
 *
 * @param key   与 formData 键一致，用于关联/回查
 * @param label 卡片展示中文标签（缺省回退 key）
 * @param value 业务系统预渲染的展示值（已格式化：金额带符号、日期本地化、布尔转「是/否」）
 */
public record DisplayField(String key, String label, String value) {
}
