package com.cim.i18n.service;

import com.cim.i18n.config.I18nProperties;
import com.cim.i18n.model.I18nScope;
import com.cim.i18n.model.LocaleStatus;
import com.cim.i18n.model.SysI18n;
import com.cim.i18n.model.SysI18nRepository;
import com.cim.i18n.model.SysLocale;
import com.cim.i18n.model.SysLocaleRepository;
import com.cim.i18n.source.BuiltinI18n;
import com.cim.i18n.source.I18nCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 多语言服务：语言目录与译文的读写 + 缓存失效 + 启动种子。
 *
 * <p>管理端接口由宿主 ap 提供（本 starter 只给服务），以便权限码归属各 ap 自己的域。</p>
 */
@Slf4j
public class I18nService {

    private static final Map<String, String> BUILTIN_LOCALE_NAMES = Map.of(
            "zh-CN", "简体中文",
            "en-US", "English");

    private final SysLocaleRepository localeRepository;
    private final SysI18nRepository i18nRepository;
    private final I18nCache cache;
    private final I18nProperties properties;

    public I18nService(SysLocaleRepository localeRepository,
                       SysI18nRepository i18nRepository,
                       I18nCache cache,
                       I18nProperties properties) {
        this.localeRepository = localeRepository;
        this.i18nRepository = i18nRepository;
        this.cache = cache;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ 读

    /** 语言目录（按排序号）。 */
    public List<SysLocale> locales() {
        return localeRepository.findAllByOrderBySortNoAsc();
    }

    /** 某语言的译文包（<b>原始</b>：仅该语言自己的行，不做兜底合并）。 */
    public Map<String, String> bundle(String locale) {
        return cache.bundle(locale);
    }

    /**
     * 前端译文包：把四级兜底链**批量合并**成「一份完整包」，供前端一次性使用。
     *
     * <p><b>为什么需要它</b>：{@link #bundle(String)} 只回该语言自己的行——若目标语言只译了一部分
     * （或压根未收录，如 {@code fr-FR}），前端就会拿到残缺/空包，界面出现空白或裸 key。
     * 而需求是「<b>翻译后展示 + 中文兜底</b>」，故此处按键做合并，优先级（低 → 高）为：</p>
     *
     * <pre>
     * 内置(默认语言) → 内置(目标语言) → DB(默认语言) → DB(目标语言)
     *    L3'              L3               L2              L1
     * </pre>
     *
     * <p>即单键解析口径与 {@code I18nResolver#resolve(...)} 一致（L1 → L2 → L3），只是这里按"包"整体做，
     * 使前端只需一次请求、无需再拉兜底包（i18next 侧亦可省掉 {@code fallbackLng} 依赖）。</p>
     *
     * <p>注：管理端「键 → 各语言」视图要看**真值/缺失**，应继续用 {@link #bundleOf(String)}，
     * 不要用本方法（合并后缺失会被兜底值掩盖）。</p>
     *
     * @param locale 目标语言（{@code null}/空白 → 默认兜底语言）
     * @return 合并后的完整包（永不 {@code null}）
     */
    public Map<String, String> frontendBundle(String locale) {
        String fallback = properties.getDefaultLocale();
        String target = isBlank(locale) ? fallback : locale;
        Map<String, String> merged = new LinkedHashMap<>();
        // 低优先级先放，高优先级覆盖：内置(兜底) → 内置(目标) → DB(兜底) → DB(目标)
        merged.putAll(BuiltinI18n.bundle(properties.getBuiltinPrefix(), fallback));
        if (!target.equals(fallback)) {
            merged.putAll(BuiltinI18n.bundle(properties.getBuiltinPrefix(), target));
        }
        merged.putAll(cache.bundle(fallback));
        if (!target.equals(fallback)) {
            merged.putAll(cache.bundle(target));
        }
        return merged;
    }

    /** 译文版本号。 */
    public String version() {
        return cache.version();
    }

    /** 译文清单（管理端；按 语言/模块/关键字 过滤）。 */
    public List<SysI18n> messages(String localeCode, String module, String keyword) {
        List<SysI18n> all = new ArrayList<>(i18nRepository.findAll());
        all.removeIf(m -> localeCode != null && !localeCode.isBlank() && !localeCode.equals(m.getLocaleCode()));
        all.removeIf(m -> module != null && !module.isBlank() && !module.equals(m.getModule()));
        all.removeIf(m -> keyword != null && !keyword.isBlank()
                && !contains(m.getCode(), keyword) && !contains(m.getContent(), keyword));
        all.sort(Comparator.comparing(SysI18n::getLocaleCode, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SysI18n::getCode, Comparator.nullsLast(Comparator.naturalOrder())));
        return all;
    }

    // ------------------------------------------------------------------ 写

    /** 保存语言（新增 / 更新，按 {@code code} upsert）。 */
    @Transactional
    public SysLocale saveLocale(SysLocale locale) {
        if (locale.getCode() == null || locale.getCode().isBlank()) {
            throw new IllegalArgumentException("locale code is required");
        }
        SysLocale target = localeRepository.findByCode(locale.getCode()).orElseGet(SysLocale::new);
        target.setCode(locale.getCode());
        target.setName(locale.getName());
        target.setSortNo(locale.getSortNo());
        target.setStatus(locale.getStatus() == null ? LocaleStatus.ENABLED : locale.getStatus());
        target.setIsDefault(Boolean.TRUE.equals(locale.getIsDefault()));
        if (Boolean.TRUE.equals(target.getIsDefault())) {
            clearOtherDefaults(target.getCode());
        }
        SysLocale saved = localeRepository.save(target);
        cache.invalidate();
        return saved;
    }

    /** 删除语言（同时删除其全部译文）。 */
    @Transactional
    public void deleteLocale(String id) {
        localeRepository.findById(id).ifPresent(locale -> {
            if (Boolean.TRUE.equals(locale.getIsDefault())) {
                throw new IllegalArgumentException("默认兜底语言不可删除：" + locale.getCode());
            }
            i18nRepository.findByLocaleCode(locale.getCode()).forEach(i18nRepository::delete);
            localeRepository.delete(locale);
            cache.invalidate();
        });
    }

    /** 保存译文（按 {@code (localeCode, code)} upsert）。 */
    @Transactional
    public SysI18n saveMessage(SysI18n message) {
        if (isBlank(message.getLocaleCode()) || isBlank(message.getCode())) {
            throw new IllegalArgumentException("localeCode and code are required");
        }
        SysI18n target = i18nRepository
                .findByLocaleCodeAndCode(message.getLocaleCode(), message.getCode())
                .orElseGet(SysI18n::new);
        target.setLocaleCode(message.getLocaleCode());
        target.setCode(message.getCode());
        target.setContent(message.getContent());
        target.setModule(message.getModule() == null ? moduleOf(message.getCode()) : message.getModule());
        target.setScope(message.getScope() == null ? I18nScope.USER : message.getScope());
        SysI18n saved = i18nRepository.save(target);
        cache.invalidate();
        return saved;
    }

    /** 删除译文。 */
    @Transactional
    public void deleteMessage(String id) {
        i18nRepository.findById(id).ifPresent(m -> {
            i18nRepository.delete(m);
            cache.invalidate();
        });
    }

    // ------------------------------------------------------------------ 种子

    /**
     * 幂等写入一批文案（<b>已存在的键不覆盖</b>），供宿主 ap 种子自己的 UI 文案（菜单/页面/按钮）。
     *
     * <p>与 {@link #saveMessage} 的区别：后者是「管理端保存」（覆盖式 upsert），本方法刻意
     * <b>不覆盖</b>已存在行——否则每次重启都会把管理员在线修改过的译文冲掉。这与 {@link #seed()}
     * 对内置文案的处理口径一致。</p>
     *
     * @param localeCode 语言（须已在 {@code sys_locale} 存在，否则由调用方先
     *                   {@link #saveLocale(SysLocale)} 建好）
     * @param messages   键 → 文案
     * @param scope      级别（宿主 ap 的 UI 文案通常 {@link I18nScope#USER}）
     * @param module     归属模块（{@code null} 时按键名首段推断，如 {@code iam.menu.orgs} → {@code iam}）
     * @return 实际新写入条数
     */
    @Transactional
    public int seedMessages(String localeCode, Map<String, String> messages,
                            I18nScope scope, String module) {
        if (isBlank(localeCode) || messages == null || messages.isEmpty()) {
            return 0;
        }
        if (localeRepository.findByCode(localeCode).isEmpty()) {
            log.warn("[cim-i18n] 跳过种子：语言 {} 未在 sys_locale 登记", localeCode);
            return 0;
        }
        int written = 0;
        for (Map.Entry<String, String> e : messages.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (i18nRepository.findByLocaleCodeAndCode(localeCode, e.getKey()).isPresent()) {
                continue;
            }
            SysI18n row = new SysI18n();
            row.setLocaleCode(localeCode);
            row.setCode(e.getKey());
            row.setContent(e.getValue());
            row.setScope(scope == null ? I18nScope.USER : scope);
            row.setModule(module == null ? moduleOf(e.getKey()) : module);
            i18nRepository.save(row);
            written++;
        }
        if (written > 0) {
            cache.invalidate();
            log.info("[cim-i18n] 宿主文案种子：{} 写入 {} 条", localeCode, written);
        }
        return written;
    }

    // ------------------------------------------------------------------ 种子

    /**
     * 启动种子（幂等）：确保内置语言存在，并把 jar 内内置文案写入 {@code sys_i18n}（scope=SYSTEM）。
     *
     * <p>已存在的键<b>不覆盖</b>——让管理端的在线修改不被重启冲掉。</p>
     */
    @Transactional
    public void seed() {
        ensureLocale(properties.getDefaultLocale(), true);
        ensureLocale("en-US", false);

        int written = 0;
        for (String locale : BUILTIN_LOCALE_NAMES.keySet()) {
            if (localeRepository.findByCode(locale).isEmpty()) {
                continue;
            }
            Map<String, String> bundle = BuiltinI18n.bundle(properties.getBuiltinPrefix(), locale);
            for (Map.Entry<String, String> e : bundle.entrySet()) {
                if (i18nRepository.findByLocaleCodeAndCode(locale, e.getKey()).isPresent()) {
                    continue;
                }
                SysI18n row = new SysI18n();
                row.setLocaleCode(locale);
                row.setCode(e.getKey());
                row.setContent(e.getValue());
                row.setScope(I18nScope.SYSTEM);
                row.setModule(moduleOf(e.getKey()));
                i18nRepository.save(row);
                written++;
            }
        }
        cache.invalidate();
        log.info("[cim-i18n] 种子完成：{} 条内置文案写入（默认语言 {}）", written, properties.getDefaultLocale());
    }

    private void ensureLocale(String code, boolean asDefault) {
        Optional<SysLocale> existing = localeRepository.findByCode(code);
        if (existing.isPresent()) {
            return;
        }
        SysLocale locale = new SysLocale();
        locale.setCode(code);
        locale.setName(BUILTIN_LOCALE_NAMES.getOrDefault(code, code));
        locale.setIsDefault(asDefault);
        locale.setSortNo(asDefault ? 0 : 10);
        locale.setStatus(LocaleStatus.ENABLED);
        localeRepository.save(locale);
    }

    private void clearOtherDefaults(String keepCode) {
        for (SysLocale other : localeRepository.findAllByOrderBySortNoAsc()) {
            if (Boolean.TRUE.equals(other.getIsDefault()) && !other.getCode().equals(keepCode)) {
                other.setIsDefault(Boolean.FALSE);
                localeRepository.save(other);
            }
        }
    }

    private static String moduleOf(String code) {
        int dot = code == null ? -1 : code.indexOf('.');
        return dot > 0 ? code.substring(0, dot) : "common";
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 供管理端「键 → 各语言」视图组装用。 */
    public Map<String, String> bundleOf(String locale) {
        Map<String, String> map = new LinkedHashMap<>(bundle(locale));
        return map;
    }
}
