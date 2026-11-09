package com.cim.notify.api;

import com.cim.notify.fanout.SseFanout;
import com.cim.notify.metrics.NotifyMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultNotifyPublisherTest {

    private final SseFanout fanout = mock(SseFanout.class);
    private final NotifyMetrics metrics = new NotifyMetrics(new SimpleMeterRegistry(), "cim.");
    private final DefaultNotifyPublisher publisher = new DefaultNotifyPublisher(fanout, metrics);

    @Test
    void publish_completesMeta_and_fanouts() {
        NotifyEvent event = new NotifyEvent("T1", NotifyEvent.TargetType.USER, List.of("u1"), "app", "t", "{}");

        publisher.publish(event);

        assertThat(event.getId()).isNotNull();
        assertThat(event.getTs()).isNotZero();
        verify(fanout).fanout(event);
    }
}
