package com.cim.mds.server.namingrule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 段配置（规则 segments JSON 的元素）——列表顺序即编码顺序。
 *
 * <p>四类型各有参数面，公共面仅 {@code type}/{@code length}：</p>
 * <ul>
 *   <li>{@code LITERAL}：{@code text} 常量文本（length 缺省 = text 长度）；</li>
 *   <li>{@code ENUM}：{@code var} 上下文变量 + {@code dictKey}（平台字典键）或
 *       {@code values}（自定义值列表）二选一，渲染时校验变量值在值域内；</li>
 *   <li>{@code DATE}：{@code pattern}（如 yyyyMMdd），按当前时间格式化；</li>
 *   <li>{@code SEQ}：{@code length} 流水宽度、{@code start} 起始值、
 *       {@code scope} 分桶口径（GLOBAL/BY_PARENT/BY_LOCATION/BY_TYPE/BY_DATE）。</li>
 * </ul>
 *
 * <p>{@code length} 为段字符长度声明：SEQ 段必填（1-9）；其余段可省略（由解析器推断），
 * 显式声明时生成结果必须等长——整码定长，扫码/下游系统按位解析的行业惯例。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SegmentSpec(
        String type,
        String text,
        String var,
        String dictKey,
        List<String> values,
        Integer length,
        String pattern,
        Long start,
        String scope) {

    public static final String TYPE_LITERAL = "LITERAL";
    public static final String TYPE_ENUM = "ENUM";
    public static final String TYPE_DATE = "DATE";
    public static final String TYPE_SEQ = "SEQ";
    public static final String TYPE_VAR = "VAR";
}
