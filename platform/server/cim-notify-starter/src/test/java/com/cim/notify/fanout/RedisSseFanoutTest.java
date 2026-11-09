package com.cim.notify.fanout;

import com.cim.notify.api.NotifyEvent;
import com.cim.notify.config.NotifyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisSseFanoutTest {

    @Mock
    private StringRedisTemplate template;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotifyProperties props = new NotifyProperties();
    private RedisSseFanout fanout;

    @BeforeEach
    void setup() {
        props.setAppCode("rms-ap");
        fanout = new RedisSseFanout(template, objectMapper, props);
    }

    @Test
    void fanout_sendsToAppCodeChannel_withSerializedEvent() throws Exception {
        NotifyEvent event = new NotifyEvent("T1", NotifyEvent.TargetType.USER, List.of("u1"), "rms-ap", "RECIPE_SIGN_OFF", "{\"k\":1}");

        fanout.fanout(event);

        ArgumentCaptor<String> channel = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(template).convertAndSend(channel.capture(), message.capture());

        assertThat(channel.getValue()).isEqualTo("cim:notify:rms-ap");
        NotifyEvent parsed = objectMapper.readValue(message.getValue(), NotifyEvent.class);
        assertThat(parsed.getTenantId()).isEqualTo("T1");
        assertThat(parsed.getTargets()).containsExactly("u1");
        assertThat(parsed.getType()).isEqualTo("RECIPE_SIGN_OFF");
    }

    @Test
    void fanout_fallsBackToPropsAppCode_whenEventAppCodeNull() {
        NotifyEvent event = new NotifyEvent("T1", NotifyEvent.TargetType.USER, List.of("u1"), null, "X", "{}");

        fanout.fanout(event);

        ArgumentCaptor<String> channel = ArgumentCaptor.forClass(String.class);
        verify(template).convertAndSend(channel.capture(), org.mockito.ArgumentMatchers.anyString());
        assertThat(channel.getValue()).isEqualTo("cim:notify:rms-ap");
    }
}
