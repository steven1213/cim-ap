package com.cim.approval.server.dto;

/**
 * 审批内容字段描述（表单 schema）：外部系统发起时随数据一起传入，
 * 详情页据此把原始键值渲染为带中文标签、分组、类型化格式的审批内容视图。
 *
 * @param key      字段标识（与 formData 的键对应，建议用稳定英文标识符）
 * @param label    展示标签（如「预算金额」，缺省回退 key）
 * @param type     类型：text | textarea | number | money | boolean | date | datetime | list
 * @param unit     单位后缀（如「元」「台」，可选）
 * @param group    分组名（如「基本信息」，可选；缺省归入「其他」）
 * @param required 是否必填提示（可选，仅展示用）
 * @param order    展示顺序（可选，越小越靠前；缺省按传入顺序）
 */
public record FormSchemaField(
        String key,
        String label,
        String type,
        String unit,
        String group,
        Boolean required,
        Integer order
) {
}
