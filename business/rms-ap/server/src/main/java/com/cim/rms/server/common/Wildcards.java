package com.cim.rms.server.common;

/**
 * 通配符查询工具（FR-F2，Req 4）。
 *
 * <p>用户语义：{@code *} 任意串、{@code ?} 单字符；SQL 侧翻译为 {@code %}/{@code _}，
 * 其余 {@code \ % _} 转义（查询须配 {@code escape '\'}）。配方与设备台账查询共用。</p>
 */
public final class Wildcards {

    private Wildcards() {
    }

    /** 空白返回 {@code null}（不过滤）；否则返回两侧带 {@code %} 的 LIKE 模式。 */
    public static String toLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : keyword.toCharArray()) {
            switch (c) {
                case '*' -> sb.append('%');
                case '?' -> sb.append('_');
                case '\\', '%', '_' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return "%" + sb + "%";
    }
}
