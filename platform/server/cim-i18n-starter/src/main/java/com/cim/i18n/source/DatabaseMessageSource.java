package com.cim.i18n.source;

import org.springframework.context.support.AbstractMessageSource;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * 数据库版 {@code MessageSource}（README §7）。
 *
 * <p>声明为 {@code @Primary} 后，Spring 校验（{@code @Valid}）、异常消息、页面取词全部走 DB，
 * 且支持热更新——原有机制<b>零改造</b>命中 DB 文案。</p>
 *
 * <p>解析链由 {@link I18nResolver} 统一实现（四级兜底），本类只做 {@code MessageFormat} 包装，
 * 保证「Spring 侧」与「{@code MessageResolver} 侧」走同一套兜底，不产生分叉。</p>
 */
public class DatabaseMessageSource extends AbstractMessageSource {

    private final I18nResolver resolver;

    public DatabaseMessageSource(I18nResolver resolver) {
        this.resolver = resolver;
        // 禁止把裸 code 当默认消息返回（与「绝不返回裸 key」的硬性要求冲突）
        setUseCodeAsDefaultMessage(false);
    }

    @Override
    protected MessageFormat resolveCode(String code, Locale locale) {
        Locale target = locale == null ? Locale.getDefault() : locale;
        String msg = resolver.resolve(code, target.toLanguageTag(), null);
        return msg == null ? null : new MessageFormat(msg, target);
    }
}
