package com.cim.obs.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏器（design.md §2.8 / T4.4）。
 *
 * <p>对日志报文中的 PII / 凭据做不可逆掩码，确保「日志无敏感信息」：
 * 手机号、邮箱、身份证、银行卡，以及 {@code key=value} 形式口令/令牌。
 * 纯静态、无 Spring 依赖，可被 Logback Converter 与应用代码共用。</p>
 */
public final class SensitiveDataMasker {

    /** 口令/令牌键名（key=value 形式掩值为 ***）。 */
    private static final Pattern KEY_SECRET =
            Pattern.compile("(password|passwd|pwd|token|secret|access[_-]?key|access[_-]?token|"
                    + "authorization|api[_-]?key|cookie)\\s*[=:]\\s*([^\"\\s=&]+)", Pattern.CASE_INSENSITIVE);
    /** 中国大陆手机号。 */
    private static final Pattern PHONE = Pattern.compile("(?<![\\d])1[3-9]\\d{9}(?![\\d])");
    /** 18 位身份证。 */
    private static final Pattern ID_CARD = Pattern.compile("(?<![\\d])\\d{17}[\\dXx](?![\\d])");
    /** 16–19 位银行卡。 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<![\\d])\\d{16,19}(?![\\d])");
    /** 邮箱。 */
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private SensitiveDataMasker() {
    }

    /** 对文本做全量脱敏；{@code null} 原样返回。 */
    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        String r = replace(KEY_SECRET, text, SensitiveDataMasker::maskKeySecret);
        r = replace(PHONE, r, g -> maskKeepEdges(g, 3, 4));
        r = replace(ID_CARD, r, g -> maskKeepEdges(g, 6, 4));
        r = replace(BANK_CARD, r, g -> maskKeepEdges(g, 0, 4));
        r = replace(EMAIL, r, SensitiveDataMasker::maskEmail);
        return r;
    }

    private static String replace(Pattern p, String in, Function<String, String> masker) {
        Matcher m = p.matcher(in);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(masker.apply(m.group())));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String maskKeySecret(String matched) {
        Matcher m = KEY_SECRET.matcher(matched);
        if (!m.find()) {
            return matched;
        }
        return m.group(1) + "=***";
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String maskedLocal = local.length() <= 1
                ? "*"
                : local.charAt(0) + "*".repeat(local.length() - 1);
        return maskedLocal + domain;
    }

    private static String maskKeepEdges(String s, int head, int tail) {
        if (s.length() <= head + tail) {
            return "*".repeat(s.length());
        }
        int maskLen = s.length() - head - tail;
        return s.substring(0, head) + "*".repeat(maskLen) + s.substring(s.length() - tail);
    }

    /** 仅用于测试/扩展的占位（当前默认规则已覆盖常见 PII）。 */
    static List<Pattern> defaultPatterns() {
        return new ArrayList<>(List.of(KEY_SECRET, PHONE, ID_CARD, BANK_CARD, EMAIL));
    }
}
