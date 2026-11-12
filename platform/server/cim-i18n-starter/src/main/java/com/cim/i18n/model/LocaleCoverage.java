package com.cim.i18n.model;

import java.util.List;

/**
 * 单个语言的译文覆盖率（管理端视图）。
 *
 * <p><b>基准集是默认兜底语言</b>：{@code total} = 默认语言有多少个键，
 * {@code translated} = 本语言在这批键里有<b>非空值</b>的数量。多出来的「本语言独有键」
 * 不计入——它们不会出现在其他语言里，算进百分比只会虚高。</p>
 *
 * <p><b>为什么不能拿 {@code frontendBundle} 算</b>：那个方法按四级兜底合并，缺失的键会被
 * 中文填满，覆盖率永远 100%。必须用 {@code bundle(locale)} 的<b>原始值</b>。</p>
 *
 * <p>{@code missing} 是缺译的键（按键名排序，超过上限时截断，见 {@code truncated}）。
 * 前端据此列出待补条目，点一条即可带着键去补录。</p>
 *
 * @param locale     语言代码（如 {@code en-US}）
 * @param name       语言显示名（如 {@code English}）
 * @param base       是否默认兜底语言（它恒为自己的基准，覆盖率 100%）
 * @param total      基准键数
 * @param translated 本语言已译条数
 * @param missing    缺译的键（可能被截断）
 * @param truncated  缺译键是否被截断
 */
public record LocaleCoverage(
        String locale,
        String name,
        boolean base,
        int total,
        int translated,
        List<String> missing,
        boolean truncated) {
}
