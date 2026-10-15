package com.cim.rms.server.spec;

/**
 * 参数规格类型（FR-S1，Req 29）。
 *
 * <ul>
 *   <li>{@link #TARGET} 目标值/绝对值——实际值须等于目标值；</li>
 *   <li>{@link #RANGE} 数值范围——实际值须落在 [min, max]；</li>
 *   <li>{@link #LIST_IN} 枚举列表——实际值须属于给定列表；</li>
 *   <li>{@link #TOLERANCE} 比例/固定值容差——实际值与目标值之差须在容差带内
 *       （百分比与固定值<b>双轨并存</b>，取满足其一即可，Req 36）。</li>
 * </ul>
 */
public enum SpecType {

    TARGET,
    RANGE,
    LIST_IN,
    TOLERANCE;

    /** 宽松解析（未知/空值降级为 {@link #TARGET}）。 */
    public static SpecType of(String s) {
        if (s == null || s.isBlank()) {
            return TARGET;
        }
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TARGET;
        }
    }
}
