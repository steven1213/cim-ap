package com.cim.approval.server.service;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.approval.server.dto.ReassignRequest;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 动态选人（改派）：将任务重新指派给目标办理人，清退原有候选，记入审计并通知新人。
 */
@Service
@RequiredArgsConstructor
public class ApprovalReassignService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional
    public void reassign(String taskId, ReassignRequest req) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "任务不存在: " + taskId);
        }
        String before = task.getAssignee();
        for (IdentityLink link : taskService.getIdentityLinksForTask(taskId)) {
            if ("candidate".equals(link.getType()) && link.getUserId() != null) {
                taskService.deleteCandidateUser(taskId, link.getUserId());
            }
        }
        taskService.setAssignee(taskId, req.newAssignee());

        String businessKey = businessKeyOf(task.getProcessInstanceId());
        auditService.append(businessKey, task.getProcessInstanceId(), taskId, "REASSIGN", req.actor(),
                before, req.newAssignee(),
                "reason=" + (req.reason() == null ? "" : req.reason()));

        notificationService.pushToUser(req.newAssignee(), "REASSIGNED",
                java.util.Map.of("taskId", taskId));
    }

    private String businessKeyOf(String procInstId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        return pi != null ? pi.getBusinessKey() : null;
    }
}
