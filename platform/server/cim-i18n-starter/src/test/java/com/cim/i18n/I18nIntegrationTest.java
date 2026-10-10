package com.cim.i18n;

import com.cim.i18n.model.I18nScope;
import com.cim.i18n.model.SysI18n;
import com.cim.i18n.service.I18nService;
import com.cim.i18n.source.DatabaseMessageSource;
import com.cim.i18n.source.I18nCache;
import com.cim.i18n.source.I18nResolver;
import com.cim.i18n.source.MissingI18nReporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多语言全链路：启动种子落库 → 四级兜底解析 → DB 版 MessageSource → 热更新版本号。
 */
@SpringBootTest(classes = TestApplication.class)
class I18nIntegrationTest {

    @Autowired
    private I18nResolver resolver;

    @Autowired
    private I18nCache cache;

    @Autowired
    private I18nService service;

    @Autowired
    private MissingI18nReporter reporter;

    @Autowired
    private MessageSource messageSource;

    @Test
    void builtinSeededAndResolvedInDefaultLocale() {
        // L2（zh-CN 兜底）命中种子写入的 DB 文案
        assertThat(resolver.resolve("common.action.save", "zh-CN", null)).isEqualTo("保存");
    }

    @Test
    void englishResolvedFromSeed() {
        assertThat(resolver.resolve("common.action.save", "en-US", null)).isEqualTo("Save");
    }

    @Test
    void unknownLocaleFallsBackToChinese() {
        // 未收录语种 → L2 兜底到 zh-CN
        assertThat(resolver.resolve("common.action.cancel", "fr-FR", null)).isEqualTo("取消");
    }

    @Test
    void explicitDefaultMessageWinsOverHumanize() {
        assertThat(resolver.resolve("no.such.key", "zh-CN", "显式兜底")).isEqualTo("显式兜底");
    }

    @Test
    void missingKeyHumanizedAndReported() {
        String msg = resolver.resolve("no.such.key", "zh-CN", null);
        assertThat(msg).isEqualTo("Key");                                    // 绝不返回裸 key
        assertThat(reporter.keys()).contains("zh-CN::no.such.key");          // 缺失已上报
    }

    @Test
    void springMessageSourceIsDatabaseDriven() {
        assertThat(messageSource).isInstanceOf(DatabaseMessageSource.class);
        assertThat(messageSource.getMessage("common.action.cancel", null,
                Locale.forLanguageTag("zh-CN"))).isEqualTo("取消");
        assertThat(messageSource.getMessage("common.action.cancel", null,
                Locale.forLanguageTag("en-US"))).isEqualTo("Cancel");
    }

    /**
     * 前端整包（{@code /api/i18n/messages} 用）必须**已合并中文兜底**——这是「翻译后展示 + 中文兜底」
     * 的落点：目标语言缺失的键要回落中文，未收录语种整体回落中文，而不是拿到残缺/空包。
     *
     * <p>此用例补的正是此前漏测的路径：{@code resolver.resolve}（单键）有兜底，但
     * {@code bundle(locale)}（整包）原样只回该语言的行 → 未收录语种回空包。见 IAM
     * {@code ConsolePermissionI18nTest} 的 {@code fr-FR} 用例。</p>
     */
    @Test
    void frontendBundleMergesChineseFallback() {
        // 只译了中文的键：英文包里也应「看得懂」（回落中文），而不是缺失
        SysI18n onlyZh = new SysI18n();
        onlyZh.setLocaleCode("zh-CN");
        onlyZh.setCode("iam.test.onlyzh");
        onlyZh.setContent("仅中文");
        onlyZh.setScope(I18nScope.USER);
        service.saveMessage(onlyZh);

        Map<String, String> en = service.frontendBundle("en-US");
        assertThat(en).containsEntry("common.action.save", "Save");        // L1 命中英文
        assertThat(en).containsEntry("iam.test.onlyzh", "仅中文");           // L1 缺 → L2 中文兜底

        // 未收录语种：整包回落中文
        assertThat(service.frontendBundle("fr-FR"))
                .containsEntry("iam.test.onlyzh", "仅中文")
                .containsEntry("common.action.save", "保存");

        // 空白 / null → 默认兜底语言
        assertThat(service.frontendBundle(null)).containsEntry("common.action.save", "保存");

        // 原始包不做兜底（管理端要看真值/缺失）——两者语义必须可区分
        assertThat(service.bundle("fr-FR")).doesNotContainKey("iam.test.onlyzh");
    }

    @Test
    void versionChangesAfterWriteAndCacheRefreshes() {
        String before = cache.version();

        SysI18n message = new SysI18n();
        message.setLocaleCode("zh-CN");
        message.setCode("iam.test.hello");
        message.setContent("你好");
        message.setScope(I18nScope.USER);
        service.saveMessage(message);

        String after = cache.version();
        assertThat(after).isNotEqualTo(before);
        assertThat(resolver.resolve("iam.test.hello", "zh-CN", null)).isEqualTo("你好");
    }
}
