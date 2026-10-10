package com.cim.i18n.source;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内置兜底文案包（四级兜底链的 L3）。
 *
 * <p>随 jar 发布的静态最小集（{@code i18n/builtin_zh-CN.properties} / {@code i18n/builtin_en-US.properties}）——
 * 防止 DB / 缓存不可用时连核心按钮文案都丢失。键与 {@code sys_i18n.code} 同构。</p>
 */
@Slf4j
public final class BuiltinI18n {

    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private BuiltinI18n() {
    }

    /** 取内置兜底文案（未命中返回 {@code null}）。 */
    public static String get(String prefix, String locale, String code) {
        Map<String, String> bundle = load(prefix, locale);
        return bundle.get(code);
    }

    /** 某语言的完整内置集（供种子写入 DB）。 */
    public static Map<String, String> bundle(String prefix, String locale) {
        return load(prefix, locale);
    }

    private static Map<String, String> load(String prefix, String locale) {
        if (locale == null || locale.isBlank()) {
            return Map.of();
        }
        return CACHE.computeIfAbsent(locale, key -> readFile(prefix + key + ".properties"));
    }

    private static Map<String, String> readFile(String resource) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = BuiltinI18n.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                return Collections.emptyMap();
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, String> map = new LinkedHashMap<>();
            props.stringPropertyNames().forEach(k -> map.put(k, props.getProperty(k)));
            log.debug("[cim-i18n] builtin bundle loaded: {} ({} keys)", resource, map.size());
            return Collections.unmodifiableMap(map);
        } catch (IOException e) {
            log.warn("[cim-i18n] failed to load builtin bundle {}: {}", resource, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
