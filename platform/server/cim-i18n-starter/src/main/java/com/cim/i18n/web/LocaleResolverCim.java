package com.cim.i18n.web;

import com.cim.i18n.config.I18nProperties;
import com.cim.i18n.source.I18nCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Locale;

/**
 * 基于 {@code Accept-Language} 的语言解析（无状态，不落 session）。
 *
 * <p>取值顺序：请求头首个语言标签 → 已知语言（译文行中出现过）→ 默认兜底语言。
 * 未识别的标签直接落到默认语言，避免「解析出一个 DB 里没有的语言 → 全量 humanize」。</p>
 */
public class LocaleResolverCim implements LocaleResolver {

    private final I18nCache cache;
    private final I18nProperties properties;

    public LocaleResolverCim(I18nCache cache, I18nProperties properties) {
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String tag = firstTag(request.getHeader("Accept-Language"));
        String fallback = properties.getDefaultLocale();
        if (tag == null) {
            return Locale.forLanguageTag(fallback);
        }
        if (known(tag)) {
            return Locale.forLanguageTag(tag);
        }
        // 语言级匹配（如客户端给 en，DB 里是 en-US）
        String language = Locale.forLanguageTag(tag).getLanguage();
        for (String candidate : cache.locales()) {
            if (candidate.toLowerCase().startsWith(language.toLowerCase())) {
                return Locale.forLanguageTag(candidate);
            }
        }
        return Locale.forLanguageTag(fallback);
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // 无状态：语言选择由前端持久化并随请求头传递，服务端不写 session
    }

    private boolean known(String tag) {
        if (tag.equalsIgnoreCase(properties.getDefaultLocale())) {
            return true;
        }
        return cache.locales().stream().anyMatch(c -> c.equalsIgnoreCase(tag));
    }

    private String firstTag(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String first = header.split(",")[0].trim();
        int semi = first.indexOf(';');
        if (semi > 0) {
            first = first.substring(0, semi).trim();
        }
        return first.isBlank() ? null : first;
    }
}
