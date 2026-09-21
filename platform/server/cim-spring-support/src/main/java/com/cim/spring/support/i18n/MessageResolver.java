package com.cim.spring.support.i18n;

/**
 * 消息解析端口：把 i18n 键 + 占位参数解析为最终文案。
 *
 * <p>{@code cim-i18n-starter} 提供基于数据库 {@code MessageSource} 的实现；缺席时由
 * {@code DefaultMessageResolver} 降级为默认模板（见 README §4 / §7）。</p>
 */
public interface MessageResolver {

    /**
     * 解析消息。
     *
     * @param key            i18n 键（可为 {@code null}）
     * @param args           占位参数
     * @param defaultMessage 键缺失时的兜底文案
     * @return 最终文案（永不返回 {@code null}）
     */
    String resolve(String key, Object[] args, String defaultMessage);
}
