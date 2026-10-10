package com.cim.i18n.source;

import com.cim.i18n.config.I18nProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 译解析核心：四级兜底链（README §7）。
 *
 * <pre>
 * L1 目标语言   DB  ← 用户当前语言
 * L2 默认兜底   DB  ← 固定 zh-CN
 * L3 内置兜底包 jar 内静态最小集
 * L4 人性化降级 humanize(code) + 上报缺失工单（绝不返回裸 key）
 * </pre>
 *
 * <p>另外：调用方传入的 {@code defaultMessage}（如 Spring 校验的默认提示）插在 L3 与 L4 之间，
 * 保证「业务显式给了兜底文案」时优先使用它。</p>
 */
@Slf4j
public class I18nResolver {

    private final I18nCache cache;
    private final MissingI18nReporter reporter;
    private final I18nProperties properties;

    public I18nResolver(I18nCache cache, MissingI18nReporter reporter, I18nProperties properties) {
        this.cache = cache;
        this.reporter = reporter;
        this.properties = properties;
    }

    /**
     * 解析文案。
     *
     * @param code           i18n 键（可为 {@code null}）
     * @param locale         目标语言标签（可为 {@code null}，回退默认语言）
     * @param defaultMessage 调用方兜底文案（可为 {@code null}）
     * @return 最终文案（永不返回 {@code null}，最差为 {@code humanize(code)}）
     */
    public String resolve(String code, String locale, String defaultMessage) {
        if (code == null || code.isBlank()) {
            return defaultMessage == null ? "" : defaultMessage;
        }
        String target = normalize(locale);
        String fallback = properties.getDefaultLocale();

        String msg = cache.get(target, code);                                   // L1
        if (msg == null && !target.equals(fallback)) {
            msg = cache.get(fallback, code);                                    // L2
        }
        if (msg == null) {
            msg = BuiltinI18n.get(properties.getBuiltinPrefix(), target, code);  // L3
        }
        if (msg == null && !target.equals(fallback)) {
            msg = BuiltinI18n.get(properties.getBuiltinPrefix(), fallback, code);
        }
        if (msg == null) {
            msg = defaultMessage;                                               // 调用方兜底
        }
        if (msg == null) {                                                      // L4
            if (properties.isMissingReportEnabled()) {
                reporter.report(code, target);
            }
            msg = humanize(code);
        }
        return msg;
    }

    private String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return properties.getDefaultLocale();
        }
        // Accept-Language 可能带权重（zh-CN,zh;q=0.9）——取首段
        String first = locale.split(",")[0].trim();
        int semi = first.indexOf(';');
        if (semi > 0) {
            first = first.substring(0, semi).trim();
        }
        return first.isBlank() ? properties.getDefaultLocale() : first;
    }

    /** {@code iam.menu.orgs} → {@code Orgs}（足够定位问题且不暴露裸 key）。 */
    private String humanize(String code) {
        String tail = code.contains(".") ? code.substring(code.lastIndexOf('.') + 1) : code;
        StringBuilder sb = new StringBuilder();
        for (String part : tail.split("[._-]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? code : sb.toString();
    }
}
