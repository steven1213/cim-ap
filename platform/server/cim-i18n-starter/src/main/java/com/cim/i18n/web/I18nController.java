package com.cim.i18n.web;

import com.cim.i18n.model.SysLocale;
import com.cim.i18n.service.I18nService;
import com.cim.i18n.source.MissingI18nReporter;
import com.cim.spring.support.web.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 国际化运行时接口（前端启动时拉取译文包）。
 *
 * <p><b>公开访问</b>：译文属展示层内容不含敏感信息，且<b>登录页也需要</b>（未认证）。
 * 宿主 ap 需在安全配置中放行 {@code /api/i18n/**}。</p>
 */
@RestController
@RequestMapping("/api/i18n")
public class I18nController {

    private final I18nService i18nService;
    private final MissingI18nReporter reporter;

    public I18nController(I18nService i18nService, MissingI18nReporter reporter) {
        this.i18nService = i18nService;
        this.reporter = reporter;
    }

    /**
     * 译文包。
     *
     * <p><b>已合并中文兜底</b>：走 {@code I18nService#frontendBundle}，按「内置(默认) → 内置(目标) →
     * DB(默认) → DB(目标)」合并，故目标语言未翻译或未收录（如 {@code fr-FR}）时仍返回完整中文文案，
     * 前端一次请求即可（见 {@code frontendBundle} 说明）。</p>
     *
     * @param lang 目标语言（缺省用默认兜底语言）
     * @param v    前端缓存的版本号；若与当前一致则只回 {@code version}、消息体为空（省流量）
     */
    @GetMapping("/messages")
    public Result<I18nBundle> messages(@RequestParam(name = "lang", required = false) String lang,
                                       @RequestParam(name = "v", required = false) String v) {
        String version = i18nService.version();
        String target = (lang == null || lang.isBlank()) ? null : lang;
        if (v != null && v.equals(version) && target != null) {
            return Result.ok(new I18nBundle(version, target, Map.of()));
        }
        String locale = target != null ? target : firstLocaleOrDefault();
        return Result.ok(new I18nBundle(version, locale, i18nService.frontendBundle(locale)));
    }

    /** 语言目录（前端语言切换下拉）。 */
    @GetMapping("/locales")
    public Result<List<SysLocale>> locales() {
        return Result.ok(i18nService.locales().stream()
                .filter(l -> l.getStatus() == com.cim.i18n.model.LocaleStatus.ENABLED)
                .toList());
    }

    /** 缺失译文上报（前三级全缺时调用，生成待翻译工单）。 */
    @PostMapping("/missing")
    public Result<Integer> missing(@RequestBody MissingReport report) {
        if (report == null || report.keys() == null) {
            return Result.ok(0);
        }
        String lang = (report.lang() == null || report.lang().isBlank()) ? null : report.lang();
        report.keys().forEach(key -> {
            if (key != null && !key.isBlank()) {
                reporter.report(key, lang == null ? "unknown" : lang);
            }
        });
        return Result.ok(report.keys().size());
    }

    private String firstLocaleOrDefault() {
        List<SysLocale> all = i18nService.locales();
        return all.isEmpty() ? "zh-CN" : all.get(0).getCode();
    }

    /**
     * 前端译文包。
     *
     * @param version  译文版本号（供前端比对与本地缓存）
     * @param lang     实际语言
     * @param messages 键 → 译文（版本未变时为空）
     */
    public record I18nBundle(String version, String lang, Map<String, String> messages) {
    }

    /** 缺失上报请求体。 */
    public record MissingReport(List<String> keys, String lang) {
    }
}
