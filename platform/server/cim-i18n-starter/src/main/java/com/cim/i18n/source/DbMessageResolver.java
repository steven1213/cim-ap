package com.cim.i18n.source;

import com.cim.spring.support.i18n.MessageResolver;
import org.springframework.context.i18n.LocaleContextHolder;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * {@link MessageResolver} 的数据库实现——顶替 {@code cim-spring-support} 的
 * {@code DefaultMessageResolver}（后者不做 i18n、直接返回兜底文案）。
 *
 * <p>装配走「接口在 spring-support、实现在本 starter」的 SPI 让位模式：
 * 引入本 starter 即生效，不引入则保持平台默认行为（可插拔）。</p>
 */
public class DbMessageResolver implements MessageResolver {

    private final I18nResolver resolver;

    public DbMessageResolver(I18nResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public String resolve(String key, Object[] args, String defaultMessage) {
        Locale locale = LocaleContextHolder.getLocale();
        String msg = resolver.resolve(key, locale.toLanguageTag(), defaultMessage);
        if (msg == null) {
            return null;
        }
        if (args != null && args.length > 0 && msg.indexOf('{') >= 0) {
            try {
                return new MessageFormat(msg, locale).format(args);
            } catch (IllegalArgumentException ex) {
                // 占位符与参数不匹配时退回原文案（不因格式化失败丢掉文案）
                return msg;
            }
        }
        return msg;
    }
}
