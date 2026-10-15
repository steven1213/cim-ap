package com.cim.rms.server.spec;

/**
 * 参数规格判定器（FR-S1/S4、FR-C4）：把「规格定义」与「实际值」比对，得出是否合规及差异类型。
 *
 * <p>被三处复用：① 上传自动参数检查（FR-C5）；② 在线比对参数级判定（FR-C4/IF-E3）；
 * ③ EC/SV 校验（FR-D2/D4）。对非数值参数（{@code TEXT}/{@code BOOL}）退化到字符串比较。
 * 数值容差<b>百分比与固定值双轨</b>（Req 36）：满足任一即通过。</p>
 */
public final class SpecEvaluator {

    private SpecEvaluator() {}

    /**
     * 判定结果。
     *
     * @param pass    是否通过
     * @param expected 规格期望（人类可读）
     * @param diffType 差异类型（未通过时）：超 target / 出 range / 不在 list / 超 tolerance / 类型非法
     */
    public record Verdict(boolean pass, String expected, String diffType) {

        public static Verdict ok(String expected) {
            return new Verdict(true, expected, null);
        }

        public static Verdict fail(String expected, String diffType) {
            return new Verdict(false, expected, diffType);
        }
    }

    /**
     * 通用判定。
     *
     * @param type      规格类型
     * @param dataType  数据类型（NUMBER / TEXT / BOOL），NUMBER 才尝试数值语义
     * @param target    目标值（TARGET/TOLERANCE 使用）
     * @param min       范围下界（RANGE 使用）
     * @param max       范围上界（RANGE 使用）
     * @param listCsv   枚举列表（LIST_IN 使用，逗号分隔）
     * @param pct       百分比容差（TOLERANCE 使用，%）
     * @param abs       固定值容差（TOLERANCE 使用）
     * @param actual    实际值（可为 {@code null}——单侧缺失）
     */
    public static Verdict evaluate(SpecType type, String dataType, String target, String min, String max,
                                   String listCsv, Double pct, Double abs, String actual) {
        SpecType t = type == null ? SpecType.TARGET : type;
        if (actual == null || actual.isBlank()) {
            return Verdict.fail(describe(t, target, min, max, listCsv, pct, abs), "单侧缺失");
        }
        boolean numeric = "NUMBER".equalsIgnoreCase(dataType == null ? "NUMBER" : dataType)
                || isNumeric(actual);
        switch (t) {
            case TARGET -> {
                if (numeric && isNumeric(target)) {
                    return eqNum(toDouble(target), toDouble(actual))
                            ? Verdict.ok(describe(t, target, min, max, listCsv, pct, abs))
                            : Verdict.fail(describe(t, target, min, max, listCsv, pct, abs), "超 target");
                }
                return target != null && target.trim().equals(actual.trim())
                        ? Verdict.ok(describe(t, target, min, max, listCsv, pct, abs))
                        : Verdict.fail(describe(t, target, min, max, listCsv, pct, abs), "超 target");
            }
            case RANGE -> {
                if (!numeric) {
                    return Verdict.fail("in[" + min + "," + max + "]", "类型非法");
                }
                double a = toDouble(actual);
                boolean lo = min == null || min.isBlank() || a >= toDouble(min);
                boolean hi = max == null || max.isBlank() || a <= toDouble(max);
                return lo && hi ? Verdict.ok("in[" + min + "," + max + "]")
                        : Verdict.fail("in[" + min + "," + max + "]", "出 range");
            }
            case LIST_IN -> {
                boolean in = false;
                if (listCsv != null) {
                    for (String v : listCsv.split(",")) {
                        if (v.trim().equalsIgnoreCase(actual.trim())) {
                            in = true;
                            break;
                        }
                    }
                }
                return in ? Verdict.ok("in{" + listCsv + "}") : Verdict.fail("in{" + listCsv + "}", "不在 list");
            }
            case TOLERANCE -> {
                if (!numeric || !isNumeric(target)) {
                    return Verdict.fail("*=" + target, "类型非法");
                }
                double tv = toDouble(target);
                double av = toDouble(actual);
                double diff = Math.abs(av - tv);
                boolean withinAbs = abs != null && abs >= 0 && diff <= abs;
                boolean withinPct = pct != null && pct >= 0 && diff <= Math.abs(tv) * pct / 100.0;
                String expected = "*=" + target + " ±" + (pct == null ? "-" : pct + "%")
                        + "/" + (abs == null ? "-" : "±" + abs);
                return (withinAbs || withinPct) ? Verdict.ok(expected) : Verdict.fail(expected, "超 tolerance");
            }
            default -> {
                return Verdict.fail(describe(t, target, min, max, listCsv, pct, abs), "类型非法");
            }
        }
    }

    private static String describe(SpecType t, String target, String min, String max,
                                   String list, Double pct, Double abs) {
        return switch (t) {
            case TARGET -> "*=" + target;
            case RANGE -> "in[" + min + "," + max + "]";
            case LIST_IN -> "in{" + list + "}";
            case TOLERANCE -> "*=" + target + " ±" + pct + "%/±" + abs;
        };
    }

    private static boolean eqNum(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static boolean isNumeric(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double toDouble(String s) {
        return Double.parseDouble(s.trim());
    }
}
