package com.cim.mds.server.flow;

import com.cim.notify.api.NotifyEvent;
import com.cim.notify.api.NotifyPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程域通知服务单测（P3-3）：灰度开关 / 接收人过滤（自己操作自己收）/ publisher 缺失兜底。
 */
class FlowNotifyServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<NotifyPublisher> provider = mock(ObjectProvider.class);
    private final NotifyPublisher publisher = mock(NotifyPublisher.class);

    private FlowNotifyService newService(boolean enabled) {
        when(provider.getIfAvailable()).thenReturn(publisher);
        FlowNotifyService s = new FlowNotifyService(provider, new com.fasterxml.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(s, "enabled", enabled);
        ReflectionTestUtils.setField(s, "appCode", "mds");
        return s;
    }

    private CimFlow flow(String occupiedBy) {
        CimFlow f = new CimFlow();
        f.setFlowCode("FLP-N1");
        f.setValidateState(CimFlow.VS_INVALID);
        f.setOccupiedBy(occupiedBy);
        return f;
    }

    @Test
    void publishToLockHolderWhenEnabled() {
        FlowNotifyService s = newService(true);
        s.validateFailed(flow("user-a"), 2, "user-b");
        org.mockito.ArgumentCaptor<NotifyEvent> cap = org.mockito.ArgumentCaptor.forClass(NotifyEvent.class);
        verify(publisher).publish(cap.capture());
        NotifyEvent e = cap.getValue();
        assertThat(e.getTargetType()).isEqualTo(NotifyEvent.TargetType.USER);
        assertThat(e.getTargets()).containsExactly("user-a");
        assertThat(e.getType()).isEqualTo("flow.validate");
        assertThat(e.getPayload()).contains("FLP-N1").contains("\"errors\":2");
    }

    @Test
    void skipWhenDisabled() {
        FlowNotifyService s = newService(false);
        s.validateFailed(flow("user-a"), 1, "user-b");
        verify(publisher, never()).publish(any());
    }

    @Test
    void skipSelfAndBlankUser() {
        FlowNotifyService s = newService(true);
        s.validateFailed(flow("user-a"), 1, "user-a"); // 自己操作自己收
        s.validateFailed(flow(null), 1, "user-b");     // 无锁占用者
        verify(publisher, never()).publish(any());
    }

    @Test
    void skipWhenPublisherMissing() {
        when(provider.getIfAvailable()).thenReturn(null);
        FlowNotifyService s = new FlowNotifyService(provider, new com.fasterxml.jackson.databind.ObjectMapper());
        ReflectionTestUtils.setField(s, "enabled", true);
        ReflectionTestUtils.setField(s, "appCode", "mds");
        s.validateFailed(flow("user-a"), 1, "user-b");
        verify(publisher, never()).publish(any());
    }
}
