package com.cim.i18n.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cim-i18n-starter 配置键（前缀 {@code cim.i18n.*}）。
 *
 * <p>与 {@code cim.auth.*} / {@code cim.jpa.*} 并列，只承载多语言相关开关。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cim.i18n")
public class I18nProperties {

    /** 总开关；关闭后不注册任何 Bean（退回 {@code DefaultMessageResolver} 行为）。 */
    private boolean enabled = true;

    /**
     * 默认兜底语言（**约定不可为空，且应存在于 {@code sys_locale}**）。
     *
     * <p>四级兜底链的 L2 命中该语言；「只要有中文就一定看得懂」。</p>
     */
    private String defaultLocale = "zh-CN";

    /** 启动是否把内置文案（jar 内 properties）幂等写入 {@code sys_i18n}（scope=SYSTEM）。 */
    private boolean seedEnabled = true;

    /** 内置兜底包（L3）路径前缀：{@code {prefix}{locale}.properties}。 */
    private String builtinPrefix = "i18n/builtin_";

    /** 是否收集缺失 key（供管理端生成待翻译工单）。 */
    private boolean missingReportEnabled = true;
}
