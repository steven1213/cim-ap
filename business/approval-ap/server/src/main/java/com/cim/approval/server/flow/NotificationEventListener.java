package com.cim.approval.server.flow;

import com.cim.approval.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Flowable 全局事件监听：任务创建/指派时向办理人推送 SSE 通知。
 *
 * <p>以 Spring Bean 形式注册到引擎事件分发器（@PostConstruct 内完成），
 * 因此可正常注入 {@link NotificationService}。</p>
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener implements FlowableEventListener {

    private final NotificationService notificationService;
    private final org.flowable.engine.ProcessEngine processEngine;

    @PostConstruct
    public void register() {
        processEngine.getProcessEngineConfiguration().getEventDispatcher().addEventListener(
                this, FlowableEngineEventType.TASK_CREATED, FlowableEngineEventType.TASK_ASSIGNED);
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEvent entityEvent)) {
            return;
        }
        Object entity = entityEvent.getEntity();
        if (!(entity instanceof Task task)) {
            return;
        }
        String assignee = task.getAssignee();
        if (assignee == null || assignee.isBlank()) {
            return;
        }
        notificationService.pushToUser(assignee, "NEW_TASK",
                java.util.Map.of(
                        "taskId", task.getId(),
                        "name", task.getName(),
                        "processInstanceId", task.getProcessInstanceId()));
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return "";
    }
}
