package com.cim.notify.transport;

import com.cim.notify.api.NotifyEvent;
import com.cim.notify.config.NotifyProperties;
import com.cim.notify.metrics.NotifyMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SseRegistryMatchingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotifyProperties props = new NotifyProperties();
    private SseRegistry registry;

    @BeforeEach
    void setup() {
        props.setAppCode("x");
        registry = new SseRegistry(props, objectMapper, new NotifyMetrics(new SimpleMeterRegistry(), "cim."));
    }

    @Test
    void resolvesUser_and_role_and_tenantIsolation() {
        registry.subscribe("T1", "u1", Set.of("R1"));
        registry.subscribe("T1", "u2", Set.of("R2"));
        registry.subscribe("T2", "u1", Set.of("R1")); // 同 userId 不同租户

        NotifyEvent toU1 = new NotifyEvent("T1", NotifyEvent.TargetType.USER, List.of("u1"), "x", "t", "{}");
        assertThat(registry.resolveTargets(toU1)).hasSize(1); // 只命中 T1.u1，排除 T2.u1

        NotifyEvent toU1U2 = new NotifyEvent("T1", NotifyEvent.TargetType.USER, List.of("u1", "u2"), "x", "t", "{}");
        assertThat(registry.resolveTargets(toU1U2)).hasSize(2);

        NotifyEvent toR1 = new NotifyEvent("T1", NotifyEvent.TargetType.ROLE, List.of("R1"), "x", "t", "{}");
        assertThat(registry.resolveTargets(toR1)).hasSize(1);

        NotifyEvent toR9 = new NotifyEvent("T1", NotifyEvent.TargetType.ROLE, List.of("R9"), "x", "t", "{}");
        assertThat(registry.resolveTargets(toR9)).isEmpty();
    }

    @Test
    void enforcesMaxConnectionsPerUser() {
        props.setMaxConnectionsPerUser(2);
        registry.subscribe("T1", "u1", Set.of());
        registry.subscribe("T1", "u1", Set.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> registry.subscribe("T1", "u1", Set.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void subscribeReturnsEmitter() {
        SseEmitter emitter = registry.subscribe("T1", "u1", Set.of());
        assertThat(emitter).isNotNull();
    }
}
