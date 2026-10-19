package com.cim.approval.sdk.dto;

/**
 * 审批内容字段描述（表单 schema）：与审批平台服务端契约一致。
 * 发起时随 formData 传入，详情页据此渲染中文标签/分组/类型。
 *
 * @param key      字段标识（与 formData 的键对应）
 * @param label    展示标签（如「预算金额」，缺省回退 key）
 * @param type     类型：text | textarea | number | money | boolean | date | datetime | list
 * @param unit     单位后缀（如「元」「台」，可选）
 * @param group    分组名（可选；缺省归入「其他」）
 * @param required 是否必填提示（可选，仅展示用）
 * @param order    展示顺序（可选，越小越靠前）
 * @param display  是否作为业务摘要（G5 兜底）：true 时在未传 displayFields 时取该字段作摘要
 */
public record FormSchemaField(
        String key,
        String label,
        String type,
        String unit,
        String group,
        Boolean required,
        Integer order,
        Boolean display
) {
}
