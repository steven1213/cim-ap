package com.cim.approval.server.service;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.approval.server.dto.AddSignRequest;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 加签：在当前审批节点增加审批人。
 *
 * <p>实现语义：将新增审批人加为任务候选人并使任务转为「候选人共享」（原办理人仍保留资格），
 * 任一人完成即推进——即<b>并行后加签</b>。type=BEFORE 时把新增人置为当前办理人、原办理人降级为候选人，
 * 表现为新增人先审。</p>
 *
 * <p>说明：Flowable 7.0.0 无稳定的运行时「独立插入子流程任务」命令（AddUserTaskCmd 不存在），
 * 严格的「串行前/后加签新建独立任务」需动态子流程注入，列为后续增强；本实现覆盖最主流的加签场景，
 * 且全程写入审计。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalAddSignService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional
    public void addSign(String taskId, AddSignRequest req) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "任务不存在: " + taskId);
        }
        String original = task.getAssignee();
        String type = req.type() == null ? "AFTER" : req.type();

        if ("BEFORE".equalsIgnoreCase(type)) {
            if (original != null) {
                taskService.addCandidateUser(taskId, original);
            }
            taskService.setAssignee(taskId, req.assignee());
        } else {
            taskService.addCandidateUser(taskId, req.assignee());
            if (original != null) {
                taskService.addCandidateUser(taskId, original);
            }
            taskService.setAssignee(taskId, null);
        }

        String businessKey = businessKeyOf(task.getProcessInstanceId());
        auditService.append(businessKey, task.getProcessInstanceId(), taskId, "ADD_SIGN", req.actor(),
                original, req.assignee(),
                "type=" + type + " reason=" + (req.reason() == null ? "" : req.reason()));

        notificationService.pushToUser(req.assignee(), "ADDED_TO_TASK",
                java.util.Map.of("taskId", taskId, "type", type));
    }

    private String businessKeyOf(String procInstId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        return pi != null ? pi.getBusinessKey() : null;
    }
}
