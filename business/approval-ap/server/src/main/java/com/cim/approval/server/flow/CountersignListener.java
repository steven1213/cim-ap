package com.cim.approval.server.flow;

import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.task.service.delegate.TaskListener;
import org.springframework.stereotype.Component;

/**
 * 会签完成监听器：每完成一个审批实例，按 outcome 在<b>流程实例级</b>累加赞成/反对计数。
 *
 * <p>以 Spring Bean 形式被 BPMN 的 {@code delegateExpression="${countersignListener}"} 引用，
 * 因此可直接注入 {@link RuntimeService}，把计数写在流程实例根执行上，保证会签完成条件与
 * 后续排他网关都能跨多实例子执行读到同一份累加值。</p>
 */
@Component
@RequiredArgsConstructor
public class CountersignListener implements TaskListener {

    private final RuntimeService runtimeService;

    @Override
    public void notify(DelegateTask delegateTask) {
        String procInstId = delegateTask.getProcessInstanceId();
        String outcome = (String) delegateTask.getVariable("outcome");
        int approved = asInt(runtimeService.getVariable(procInstId, "approvedCount"), 0);
        int rejected = asInt(runtimeService.getVariable(procInstId, "rejectedCount"), 0);
        if ("APPROVE".equals(outcome)) {
            approved++;
        } else {
            rejected++;
        }
        runtimeService.setVariable(procInstId, "approvedCount", approved);
        runtimeService.setVariable(procInstId, "rejectedCount", rejected);
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
