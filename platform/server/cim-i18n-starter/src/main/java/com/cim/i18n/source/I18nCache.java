package com.cim.i18n.source;

import com.cim.i18n.model.SysI18n;
import com.cim.i18n.model.SysI18nRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 译文缓存：一次性把 {@code sys_i18n} 载入内存快照，供 {@link I18nResolver} 零 join 命中。
 *
 * <p><b>热更新</b>：任何管理端写入调用 {@link #invalidate()} 后，下次访问重新装载
 * （i18n 体量小，全量重载成本极低，换来实现极简且永远一致）。</p>
 *
 * <p>若后续接入 {@code cim-cache-starter} 多级缓存 + 跨节点失效，只需替换本类的装载来源，
 * 对外契约（{@code get/bundle/version}）不变。</p>
 */
@Slf4j
public class I18nCache {

    private final SysI18nRepository repository;

    private volatile Snapshot snapshot;

    public I18nCache(SysI18nRepository repository) {
        this.repository = repository;
    }

    /** 命中某语言下的键（未命中返回 {@code null}）。 */
    public String get(String locale, String code) {
        if (locale == null || code == null) {
            return null;
        }
        return snapshot().messages().getOrDefault(locale, Map.of()).get(code);
    }

    /** 某语言的完整译文包（副本）。 */
    public Map<String, String> bundle(String locale) {
        if (locale == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(snapshot().messages().getOrDefault(locale, Map.of()));
    }

    /** 已知语言标签集（来自译文行）。 */
    public Set<String> locales() {
        return snapshot().messages().keySet();
    }

    /** 译文版本号（前端按此比对决定是否重拉）。 */
    public String version() {
        return snapshot().version();
    }

    /** 失效，下次访问重载。 */
    public void invalidate() {
        snapshot = null;
    }

    private Snapshot snapshot() {
        Snapshot local = snapshot;
        if (local == null) {
            synchronized (this) {
                local = snapshot;
                if (local == null) {
                    local = load();
                    snapshot = local;
                }
            }
        }
        return local;
    }

    private Snapshot load() {
        List<SysI18n> rows = repository.findAll();
        Map<String, Map<String, String>> messages = new LinkedHashMap<>();
        for (SysI18n row : rows) {
            if (row.getLocaleCode() == null || row.getCode() == null) {
                continue;
            }
            messages.computeIfAbsent(row.getLocaleCode(), k -> new LinkedHashMap<>())
                    .put(row.getCode(), row.getContent() == null ? "" : row.getContent());
        }
        LocalDateTime max = repository.maxEventTime();
        String version = (max == null ? "0" : max.toString()) + "#" + rows.size();
        log.debug("[cim-i18n] snapshot loaded: {} locales, {} rows, version={}",
                messages.size(), rows.size(), version);
        return new Snapshot(immutable(messages), version);
    }

    private Map<String, Map<String, String>> immutable(Map<String, Map<String, String>> src) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k, Map.copyOf(v)));
        return Map.copyOf(out);
    }

    /** 快照：语言 → (键 → 译文) + 版本号。 */
    public record Snapshot(Map<String, Map<String, String>> messages, String version) {
    }
}
