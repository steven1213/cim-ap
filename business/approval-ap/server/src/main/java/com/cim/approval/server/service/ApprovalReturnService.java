package com.cim.approval.server.service;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.approval.server.dto.ReturnRequest;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回退（jump back）：将当前任务执行流跳回指定节点（默认上一用户任务节点）。
 *
 * <p>基于 Flowable {@code ChangeActivityStateBuilder.moveExecutionToActivityId} 实现，
 * 跳转动因记入审计，禁止静默回退。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalReturnService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final AuditService auditService;

    public void returnTask(String taskId, ReturnRequest req) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "任务不存在: " + taskId);
        }
        String procInstId = task.getProcessInstanceId();
        String target = req.targetActivityId();
        if (target == null || target.isBlank()) {
            List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(procInstId)
                    .activityType("userTask")
                    .finished()
                    .orderByHistoricActivityInstanceEndTime().desc()
                    .list();
            if (acts.isEmpty()) {
                throw new BizException(BizCode.PARAM_INVALID, "无可回退的上一用户任务节点");
            }
            target = acts.get(0).getActivityId();
        }

        runtimeService.createChangeActivityStateBuilder()
                .moveExecutionToActivityId(task.getExecutionId(), target)
                .changeState();

        String businessKey = businessKeyOf(procInstId);
        auditService.append(businessKey, procInstId, taskId, "RETURN", req.actor(),
                task.getName(), target,
                "reason=" + (req.reason() == null ? "" : req.reason()) + " target=" + target);
    }

    private String businessKeyOf(String procInstId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        return pi != null ? pi.getBusinessKey() : null;
    }
}
