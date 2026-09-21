package com.cim.obs.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class MaskedMessageConverterTest {

    @Test
    @DisplayName("Logback 转换器对落盘报文脱敏")
    void convertsMaskedMessage() {
        ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("login pwd=topsecret99 phone=13912345678");

        MaskedMessageConverter converter = new MaskedMessageConverter();
        String out = converter.convert(event);

        assertTrue(out.contains("pwd=***"), out);
        assertTrue(out.contains("139****5678"), out);
        assertFalse(out.contains("topsecret99"), "明文口令不得落盘");
    }
}
