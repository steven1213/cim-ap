package com.cim.i18n.source;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缺失译文上报（README §7 第 ④ 级兜底）。
 *
 * <p>四级兜底链全部未命中时记录 {@code locale::code}，供管理端生成待翻译工单；
 * <b>绝不静默失败</b>。内存收集，重启即清（待翻译工单是运维态，不需要持久化）。</p>
 */
@Slf4j
public class MissingI18nReporter {

    private final Map<String, Instant> missing = new ConcurrentHashMap<>();

    /** 记录一次缺失。 */
    public void report(String code, String locale) {
        if (code == null || code.isBlank()) {
            return;
        }
        String key = locale + "::" + code;
        missing.putIfAbsent(key, Instant.now());
        log.warn("[cim-i18n] 缺失译文：code={} locale={}（已上报，可到「多语言」页补录）", code, locale);
    }

    /** 缺失清单（只读快照）。 */
    public Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        missing.forEach((k, v) -> result.put(k, v.toString()));
        return result;
    }

    /** 缺失键集合（{@code locale::code}）。 */
    public Set<String> keys() {
        return Set.copyOf(missing.keySet());
    }

    /** 清空。 */
    public void clear() {
        missing.clear();
    }
}
