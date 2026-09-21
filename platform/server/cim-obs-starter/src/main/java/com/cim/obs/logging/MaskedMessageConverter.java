package com.cim.obs.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.pattern.ClassicConverter;

/**
 * Logback 报文脱敏转换器（design.md §2.8 / T4.4）。
 *
 * <p>在 {@code logback-spring.xml} 中以 {@code %maskedMsg} 替代 {@code %msg}，即可让落盘日志
 * 自动脱敏（需本 starter 在 classpath，且 logback 为日志实现）。脱敏规则见
 * {@link SensitiveDataMasker}。</p>
 *
 * <pre>{@code
 * <conversionRule conversionWord="maskedMsg" converterClass="com.cim.obs.logging.MaskedMessageConverter"/>
 * <pattern>%d ... %maskedMsg%n</pattern>
 * }</pre>
 */
public class MaskedMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataMasker.mask(event.getFormattedMessage());
    }
}
