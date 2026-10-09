package com.cim.iam.server.admin;

import com.cim.i18n.model.SysI18n;
import com.cim.i18n.model.SysLocale;
import com.cim.i18n.service.I18nService;
import com.cim.i18n.source.MissingI18nReporter;
import com.cim.iam.server.support.IamPermissionCodes;
import com.cim.spring.support.web.Result;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 多语言管理接口（IAM 控制台「多语言」页的数据面）。
 *
 * <p><b>为什么由 IAM 提供而不是 i18n starter</b>：语言/译文的管理属「某个 ap 的内部权限」，
 * 权限码与菜单应由各 ap 自己的域定义（starter 只给 {@link I18nService} 服务与公开的
 * {@code /api/i18n/**} 运行时接口）。见 {@code console-menu-perm-i18n.md} §6。</p>
 *
 * <p>类级保留 {@code iam:console:admin} 兜底（未逐个标注的方法退回该码），
 * 方法级为细粒度权限码——方法级优先于类级。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/i18n")
@PreAuthorize("hasAuthority('" + IamPermissionCodes.CONSOLE_ADMIN + "')")
public class I18nAdminController {

    private final I18nService i18nService;
    private final MissingI18nReporter missingReporter;

    public I18nAdminController(I18nService i18nService, MissingI18nReporter missingReporter) {
        this.i18nService = i18nService;
        this.missingReporter = missingReporter;
    }

    // ------------------------------------------------------------------ 语言目录

    /** 全部语言（含停用，管理视角）。 */
    @GetMapping("/locales")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.I18N_LIST + "')")
    public Result<List<SysLocale>> locales() {
        return Result.ok(i18nService.locales());
    }

    /** 新增 / 更新语言（按 code upsert；默认语言互斥）。 */
    @PostMapping("/locales")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.I18N_LOCALE + "')")
    public Result<SysLocale> saveLocale(@RequestBody SysLocale locale) {
        return Result.ok(i18nService.saveLocale(locale));
    }

    /** 删除语言（默认兜底语言不可删）。 */
    @DeleteMapping("/locales/{id}")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.I18N_LOCALE + "')")
    public Result<Void> deleteLocale(@PathVariable String id) {
        i18nService.deleteLocale(id);
        return Result.ok();
    }

    // ------------------------------------------------------------------ 译文

    /** 译文清单（按语言/模块/关键字过滤）。 */
    @GetMapping("/messages")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.I18N_LIST + "')")
    public Result<List<SysI18n>> messages(
            @RequestParam(name = "localeCode", required = false) String localeCode,
            @RequestParam(name = "module", required = false) String module,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return Result.ok(i18nService.messages(localeCode, module, keyword));
    }

    /** 新增 / 更新译文（按 {@code (localeCode, code)} upsert，即「保存」动作）。 */
    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.I18N_CREATE + "')")
    public Result<SysI18n> saveMessage(@RequestBody SysI18n message) {
        return Result.ok(i18nService.saveMessage(message));
    }

    /** 删除译文。 */
    @DeleteMapping("/messages/{id}")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.I18N_DELETE + "')")
    public Result<Void> deleteMessage(@PathVariable String id) {
        i18nService.deleteMessage(id);
        return Result.ok();
    }

    /** 当前译文版本号（前端缓存比对）。 */
    @GetMapping("/version")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.I18N_LIST + "')")
    public Result<String> version() {
        return Result.ok(i18nService.version());
    }

    // ------------------------------------------------------------------ 缺失译文

    /** 缺失译文汇总（四级兜底全未命中的键，供生成待翻译工单）。 */
    @GetMapping("/missing")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.I18N_LIST + "')")
    public Result<Map<String, String>> missing() {
        return Result.ok(missingReporter.snapshot());
    }

    /** 清空缺失译文记录。 */
    @DeleteMapping("/missing")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.I18N_DELETE + "')")
    public Result<Void> clearMissing() {
        missingReporter.clear();
        return Result.ok();
    }
}
