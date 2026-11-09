package com.cim.jpa.sqllog;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.UUID;

/**
 * 把带 {@code ?} 占位符的 SQL 渲染成「可直接在数据库客户端粘贴执行」的单行 SQL（仅调试用）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>占位符按 JDBC 下标（从 1 起）替换为类型化字面量：字符串/时间/UUID 加引号并转义单引号，
 *       数字/布尔原样，{@code null} → {@code NULL}，{@code byte[]} → {@code X'..'}，
 *       流/LOB → {@code '<stream>'}（不可执行，仅标识）；</li>
 *   <li>只替换**字符串字面量与注释之外**的 {@code ?}（如 {@code like '%?%'} 不受影响）；</li>
 *   <li>折叠空白与注释为单行，便于 grep 与粘贴；</li>
 *   <li>未赋值的占位符保留 {@code ?}（说明调用方漏设参数）。</li>
 * </ul>
 *
 * <p>⚠️ 输出仅用于排障：单参数超过 512 字符会被截断、换行被替换为空格，
 * 超长文本参数渲染出的 SQL 与原值**不完全等价**。</p>
 */
public final class SqlLogFormatter {

    /** 单参数最大渲染长度（超出截断，防止 CLOB 撑爆日志）。 */
    static final int MAX_PARAM_LENGTH = 512;

    private static final String PARAM_TRUNCATED = "...<truncated>";
    private static final String SQL_TRUNCATED = " ...<truncated>";

    private SqlLogFormatter() {
    }

    /**
     * 渲染可执行 SQL。
     *
     * @param sql       带 {@code ?} 的原始 SQL
     * @param params    JDBC 下标（1 起）→ 参数值
     * @param maxLength 输出最大长度，{@code <=0} 取 4000
     */
    public static String toExecutable(String sql, Map<Integer, Object> params, int maxLength) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        int max = maxLength > 0 ? maxLength : 4000;
        StringBuilder out = new StringBuilder(Math.min(sql.length() + 64, max + 64));
        int placeholder = 0;
        int i = 0;
        int n = sql.length();
        char quote = 0;
        boolean aborted = false;

        while (i < n) {
            if (out.length() >= max) {
                aborted = true;
                break;
            }
            char c = sql.charAt(i);

            if (quote != 0) {
                // 字符串/标识符字面量内部：原样拷贝，仅压平换行
                if (c == quote) {
                    out.append(c);
                    if (i + 1 < n && sql.charAt(i + 1) == quote) { // '' 转义
                        out.append(quote);
                        i += 2;
                        continue;
                    }
                    quote = 0;
                    i++;
                    continue;
                }
                out.append(isLineBreak(c) ? ' ' : c);
                i++;
                continue;
            }

            if (c == '\'' || c == '"') {
                quote = c;
                out.append(c);
                i++;
                continue;
            }
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && !isLineBreak(sql.charAt(i))) {
                    i++;
                }
                appendSpace(out);
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                appendSpace(out);
                continue;
            }
            if (c == '?') {
                placeholder++;
                boolean bound = params != null && params.containsKey(placeholder);
                out.append(bound ? literal(params.get(placeholder)) : "?");
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                appendSpace(out);
                while (i < n && Character.isWhitespace(sql.charAt(i))) {
                    i++;
                }
                continue;
            }
            out.append(c);
            i++;
        }

        int end = out.length();
        while (end > 0 && out.charAt(end - 1) == ' ') {
            end--;
        }
        String rendered = out.substring(0, end);
        if (aborted || rendered.length() > max) {
            return withMarker(rendered, max);
        }
        return rendered;
    }

    /** 截到 {@code max} 并追加截断标记（标记前保留一个空格，便于肉眼识别）。 */
    private static String withMarker(String sql, int max) {
        String body = sql.length() > max ? sql.substring(0, max) : sql;
        int end = body.length();
        while (end > 0 && body.charAt(end - 1) == ' ') {
            end--;
        }
        return body.substring(0, end) + SQL_TRUNCATED;
    }

    /** 参数值 → SQL 字面量。 */
    public static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number number) {
            if (number instanceof Double d && (d.isNaN() || d.isInfinite())
                    || number instanceof Float f && (f.isNaN() || f.isInfinite())) {
                return quote(number.toString());
            }
            return number instanceof BigDecimal decimal ? decimal.toPlainString() : number.toString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        if (value instanceof byte[] bytes) {
            return bytes.length > MAX_PARAM_LENGTH / 2
                    ? quote("<bytes len=" + bytes.length + ">")
                    : "X'" + hex(bytes) + "'";
        }
        if (value instanceof java.sql.Date date) {
            return quote(date.toString());
        }
        if (value instanceof java.sql.Time time) {
            return quote(time.toString());
        }
        if (value instanceof Timestamp timestamp) {
            return quote(timestamp.toString());
        }
        if (value instanceof java.util.Date date) {
            return quote(new Timestamp(date.getTime()).toString());
        }
        if (value instanceof TemporalAccessor) {
            return quote(value.toString());
        }
        if (value instanceof InputStream || value instanceof Reader) {
            return quote("<stream>");
        }
        if (value instanceof CharSequence || value instanceof Character
                || value instanceof Enum<?> || value instanceof UUID) {
            return quote(value.toString());
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String text) {
        String v = text.length() > MAX_PARAM_LENGTH
                ? text.substring(0, MAX_PARAM_LENGTH) + PARAM_TRUNCATED + "<len=" + text.length() + ">"
                : text;
        StringBuilder q = new StringBuilder(v.length() + 2).append('\'');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\'') {
                q.append("''");
            } else if (isLineBreak(c) || c == '\0') {
                q.append(' ');
            } else {
                q.append(c);
            }
        }
        return q.append('\'').toString();
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r' || c == '\t';
    }

    private static void appendSpace(StringBuilder out) {
        int len = out.length();
        if (len > 0 && out.charAt(len - 1) != ' ') {
            out.append(' ');
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
