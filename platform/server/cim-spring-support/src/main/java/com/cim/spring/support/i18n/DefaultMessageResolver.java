package com.cim.spring.support.i18n;

/**
 * 默认消息解析实现：直接返回兜底文案（不做 i18n）。
 *
 * <p>由 {@code SpringSupportAutoConfiguration} 以
 * {@code @ConditionalOnMissingBean(MessageResolver.class)} 注册，
 * 当 {@code cim-i18n-starter} 存在时会被其数据库实现覆盖（见 README §8 可插拔）。</p>
 */
public class DefaultMessageResolver implements MessageResolver {

    @Override
    public String resolve(String key, Object[] args, String defaultMessage) {
        return defaultMessage;
    }
}
