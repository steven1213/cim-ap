package com.cim.approval.server.service;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.approval.server.dto.ReturnRequest;
import com.cim.approval.server.dto.ReturnTargetsView;
import com.cim.approval.server.notification.NotificationDispatcher;
import com.cim.approval.server.notification.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 退回业务流程（Product-Framework §2.2）。
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li><b>重提模式（退到发起人）</b>：执行流迁回首个用户任务节点，任务改派发起人并改名
 *       「修改并重新提交」，设 {@code awaitingResubmit}；发起人修改表单重提后，任务改回原审批人，
 *       审批链从头重走（条件按新表单值重评，版本不变）。仅首节点为单人审批节点时提供。</li>
 *   <li><b>重审模式（退到上一节点/指定节点）</b>：执行流迁回目标已审批节点，任务重建给原审批人重审。</li>
 * </ul>
 *
 * <p>通用规则：轮次 {@code round}+1 且不超过 {@code approval.return.max-rounds}（默认 5）；
 * 会签/并行段的同实例其他在途任务取消并通知（对齐初稿 BR-03/BR-13 收尾语义）；
 * 会签计数（approvedCount/rejectedCount）重置，保证重提/重审后完成条件正确；
 * 退回目标仅限已审批过的用户节点；并行段内仅允许退到发起人或并行段之前的节点（BR-05）。</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalReturnService {

    /** 重提态任务名（发起人在待办里看到的修改入口）。 */
    public static final String RESUBMIT_TASK_NAME = "修改并重新提交";

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final AuditService auditService;
    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @Value("${approval.return.max-rounds:5}")
    private int maxRounds;

    /**
     * 退回当前任务。
     *
     * @param taskId 当前任务
     * @param req    目标节点（为空=退到发起人重提模式；为首节点 id 时等同重提模式）、原因、操作人
     */
    @Transactional
    public void returnTask(String taskId, ReturnRequest req) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "任务不存在: " + taskId);
        }
        String procInstId = task.getProcessInstanceId();
        String businessKey = businessKeyOf(procInstId);
        String initiator = (String) runtimeService.getVariable(procInstId, "initiator");
        int currentRound = readRound(procInstId);
        if (currentRound >= maxRounds) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "已达退回上限（" + maxRounds + " 轮），请联系流程管理员处理");
        }

        // 目标判定：未传或传首节点 => 重提模式（退到发起人）；否则重审模式
        String firstNode = firstUserTaskActivityId(procInstId);
        boolean resubmitMode = req.targetActivityId() == null || req.targetActivityId().isBlank()
                || firstNode.equals(req.targetActivityId());
        String target;
        if (resubmitMode) {
            if (isMultiInstanceNode(procInstId, firstNode)) {
                throw new BizException(BizCode.PARAM_INVALID,
                        "首节点为多人审批（会签）节点，不支持退到发起人重提，请退到指定节点重审");
            }
            target = firstNode;
        } else {
            target = req.targetActivityId();
            if (!finishedUserTaskIds(procInstId).contains(target)) {
                throw new BizException(BizCode.PARAM_INVALID, "退回目标必须是已审批过的节点");
            }
            if (isParallelActive(procInstId) && !isPreParallelNode(procInstId, target)) {
                throw new BizException(BizCode.PARAM_INVALID,
                        "并行审批段内仅允许退到发起人或并行段之前的节点");
            }
        }

        // E1：通知同实例其他在途任务受理人（会签未处理项/并行分支将被合流取消）。
        // 注意：运行中的任务不能直接 deleteTask（Flowable 抛「cannot be deleted because is part of a running process」），
        // 统一通过下方执行流迁移合流，被合流的执行其任务随之消失。
        for (Task other : otherActiveTasks(procInstId, taskId)) {
            String assignee = other.getAssignee();
            if (assignee != null && !assignee.isBlank()) {
                dispatcher.dispatch(NotificationEvent.of(NotificationEvent.Type.TASK_CANCELLED,
                        java.util.List.of(assignee), Map.of(
                                "taskId", other.getId(),
                                "processInstanceId", procInstId,
                                "reason", "流程已退回，任务取消")));
            }
        }

        // 会签计数重置：重提/重审后审批链从头走，旧计数会导致完成条件误判
        if (runtimeService.getVariable(procInstId, "approvedCount") != null) {
            runtimeService.setVariable(procInstId, "approvedCount", 0);
            runtimeService.setVariable(procInstId, "rejectedCount", 0);
        }

        boolean sameActivity = target.equals(task.getTaskDefinitionKey());
        if (!sameActivity) {
            // 收集当前实例所有在途任务对应的执行（会签/并行段下 >1），统一迁到目标单节点实现合流：
            // 单执行=顺序流，直接单执行迁移；多执行=会签/并行，合并为单一目标任务（其余分支随之取消）。
            List<String> leafExecIds = taskService.createTaskQuery()
                    .processInstanceId(procInstId).active().list().stream()
                    .map(Task::getExecutionId).distinct().toList();
            if (leafExecIds.size() <= 1) {
                runtimeService.createChangeActivityStateBuilder()
                        .moveExecutionToActivityId(task.getExecutionId(), target)
                        .changeState();
            } else {
                runtimeService.createChangeActivityStateBuilder()
                        .moveExecutionsToSingleActivityId(leafExecIds, target)
                        .changeState();
            }
        }

        int newRound = currentRound + 1;
        runtimeService.setVariable(procInstId, "round", newRound);

        boolean awaitingResubmit = false;
        if (resubmitMode) {
            // 目标节点任务（可能因并行展开出多个）仅保留一个，改派发起人并改名
            List<Task> atTarget = activeTasksAt(procInstId, target);
            Task mine = null;
            for (Task t : atTarget) {
                if (mine == null) {
                    mine = t;
                } else {
                    taskService.deleteTask(t.getId(), "RETURN_CANCELLED");
                }
            }
            if (mine != null) {
                String originalName = mine.getName();
                String originalAssignee = mine.getAssignee();
                renameTask(mine, RESUBMIT_TASK_NAME);
                taskService.setAssignee(mine.getId(), initiator);
                runtimeService.setVariable(procInstId, "resubmitOriginalAssignee", originalAssignee);
                runtimeService.setVariable(procInstId, "resubmitOriginalTaskName", originalName);
                awaitingResubmit = true;
            }
        }
        runtimeService.setVariable(procInstId, "awaitingResubmit", awaitingResubmit);

        auditService.append(businessKey, procInstId, taskId, "RETURN", req.actor(),
                task.getName(), target,
                "reason=" + (req.reason() == null ? "" : req.reason())
                        + " target=" + target + " round=" + newRound
                        + " mode=" + (awaitingResubmit ? "RESUBMIT" : "REVIEW"));

        if (initiator != null && !initiator.isBlank()) {
            dispatcher.dispatch(NotificationEvent.of(NotificationEvent.Type.TASK_RETURNED,
                    java.util.List.of(initiator), Map.of(
                            "processInstanceId", procInstId,
                            "taskName", task.getName(),
                            "reason", req.reason() == null ? "" : req.reason(),
                            "round", newRound,
                            "awaitingResubmit", awaitingResubmit)));
        }
    }

    /**
     * 重新提交：发起人修改表单后重提，审批链从头重走。
     *
     * <p>单人节点=任务改回原审批人并恢复原名（不推进流程，原审批人重新审批即触发全链重走）；
     * 候选节点（原无受理人）=受理人置空回归候选态。会签计数已在退回时重置。</p>
     */
    @Transactional
    public void resubmit(String instanceId, com.cim.approval.server.dto.ResubmitRequest req) {
        String actor = req.actor();
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId).singleResult();
        if (pi == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "流程实例不存在或已结束");
        }
        String initiator = (String) runtimeService.getVariable(instanceId, "initiator");
        if (initiator == null || !initiator.equals(actor)) {
            throw new BizException(BizCode.PARAM_INVALID, "仅发起人可重新提交");
        }
        if (!Boolean.TRUE.equals(runtimeService.getVariable(instanceId, "awaitingResubmit"))) {
            throw new BizException(BizCode.PARAM_INVALID, "当前无可重新提交的任务（仅退回到发起人后可重提）");
        }
        List<Task> mine = taskService.createTaskQuery()
                .processInstanceId(instanceId).active().taskAssignee(actor).list();
        if (mine.isEmpty()) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "未找到待重新提交的任务");
        }
        Task myTask = mine.get(0);
        String businessKey = pi.getBusinessKey();
        Integer round = (Integer) runtimeService.getVariable(instanceId, "round");

        // 表单数据更新（与发起同口径：64KB 上限）
        updateFormVars(instanceId, req);

        String originalAssignee = (String) runtimeService.getVariable(instanceId, "resubmitOriginalAssignee");
        String originalName = (String) runtimeService.getVariable(instanceId, "resubmitOriginalTaskName");
        if (originalName != null) {
            renameTask(myTask, originalName);
        }
        // 受理人置空=回归候选态（原节点为候选审批时）；否则改回原受理人（触发 TASK_ASSIGNED 通知）
        taskService.setAssignee(myTask.getId(), originalAssignee);

        runtimeService.setVariable(instanceId, "awaitingResubmit", false);
        runtimeService.removeVariable(instanceId, "resubmitOriginalAssignee");
        runtimeService.removeVariable(instanceId, "resubmitOriginalTaskName");

        auditService.append(businessKey, instanceId, myTask.getId(), "RESUBMIT", actor,
                "ROUND_" + round, "ROUND_" + round,
                "round=" + round + " mode=RESUBMIT");
    }

    /** 退回弹窗数据源：目标选项 + 轮次 + 上限 + 并行段状态。 */
    public ReturnTargetsView returnTargets(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "任务不存在: " + taskId);
        }
        String procInstId = task.getProcessInstanceId();
        List<HistoricActivityInstance> finished = finishedUserTasks(procInstId);
        String firstNode = firstUserTaskActivityId(procInstId);

        String previousId = null;
        String previousName = null;
        if (!finished.isEmpty()) {
            previousId = finished.get(0).getActivityId();
            previousName = finished.get(0).getActivityName();
        }
        List<ReturnTargetsView.NodeOption> options = new ArrayList<>();
        for (HistoricActivityInstance a : finished) {
            if (!a.getActivityId().equals(firstNode)) {
                options.add(new ReturnTargetsView.NodeOption(a.getActivityId(), a.getActivityName()));
            }
        }
        return new ReturnTargetsView(
                previousId, previousName,
                firstNode, firstNodeName(procInstId, firstNode),
                isMultiInstanceNode(procInstId, firstNode),
                options,
                readRound(procInstId), maxRounds,
                isParallelActive(procInstId));
    }

    // ---------- 内部 helpers ----------

    private int readRound(String procInstId) {
        Object round = runtimeService.getVariable(procInstId, "round");
        return round instanceof Integer i ? i : 1;
    }

    private void updateFormVars(String instanceId, com.cim.approval.server.dto.ResubmitRequest req) {
        try {
            if (req.formData() != null && !req.formData().isEmpty()) {
                String json = objectMapper.writeValueAsString(req.formData());
                if (json.length() > 65_536) {
                    throw new BizException(BizCode.PARAM_INVALID,
                            "审批内容过大（" + json.length() + " > 65536 字符），请精简或改走附件");
                }
                runtimeService.setVariable(instanceId, "formDataJson", json);
            }
            if (req.formSchema() != null && !req.formSchema().isEmpty()) {
                runtimeService.setVariable(instanceId, "formSchemaJson",
                        objectMapper.writeValueAsString(req.formSchema()));
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "表单数据序列化失败: " + e.getMessage());
        }
    }

    private void renameTask(Task task, String newName) {
        if (task instanceof TaskEntity entity) {
            entity.setName(newName);
            taskService.saveTask(entity);
        }
    }

    private List<Task> otherActiveTasks(String procInstId, String excludeTaskId) {
        return taskService.createTaskQuery().processInstanceId(procInstId).active().list()
                .stream().filter(t -> !t.getId().equals(excludeTaskId)).toList();
    }

    private List<Task> activeTasksAt(String procInstId, String activityId) {
        return taskService.createTaskQuery().processInstanceId(procInstId).active()
                .taskDefinitionKey(activityId).list();
    }

    private List<HistoricActivityInstance> finishedUserTasks(String procInstId) {
        return historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInstId)
                .activityType("userTask")
                .finished()
                .orderByHistoricActivityInstanceEndTime().desc()
                .list();
    }

    private List<String> finishedUserTaskIds(String procInstId) {
        List<String> ids = new ArrayList<>();
        for (HistoricActivityInstance a : finishedUserTasks(procInstId)) {
            ids.add(a.getActivityId());
        }
        return ids;
    }

    /** 首个用户任务活动 id（发起人节点：重提模式的迁移目标）。 */
    private String firstUserTaskActivityId(String procInstId) {
        List<HistoricActivityInstance> acts = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInstId)
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
        if (acts.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "实例内无用户任务节点，无法回退");
        }
        return acts.get(0).getActivityId();
    }

    private String firstNodeName(String procInstId, String firstNode) {
        return historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInstId)
                .activityType("userTask")
                .orderByHistoricActivityInstanceStartTime().asc()
                .list().stream()
                .filter(a -> a.getActivityId().equals(firstNode))
                .map(HistoricActivityInstance::getActivityName)
                .findFirst().orElse(firstNode);
    }

    /**
     * 指定节点当前是否为多人（并行会签）活跃节点：以「此刻该活动下并发在途任务数 > 1」判定。
     * <p>不能用历史完成数：顺序流退回到首节点重提后，首节点会被重复完成多次，历史计数误判为会签。
     * 只有真正并行展开的会签，同一时刻才会有多条在途任务。</p>
     */
    private boolean isMultiInstanceNode(String procInstId, String activityId) {
        return taskService.createTaskQuery()
                .processInstanceId(procInstId)
                .taskDefinitionKey(activityId)
                .active()
                .list().size() > 1;
    }

    /**
     * 实例当前是否处于并行段（BR-05 判定）。
     * <p>不能按 execution 计数：顺序流在 userTask 处也恒有 2 个 execution（根 + 叶）。
     * 改用活动节点 id 数——仅并行网关展开时才 >1（多实例节点仍是单活动 id）。</p>
     */
    private boolean isParallelActive(String procInstId) {
        try {
            List<String> ids = runtimeService.getActiveActivityIds(procInstId);
            return ids != null && ids.size() > 1;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 目标是否为并行段之前的节点：以首个并行网关活动的开始时间为界，
     * 目标用户任务的结束时间早于该时刻即视为并行段前节点（BR-05）。
     */
    private boolean isPreParallelNode(String procInstId, String targetActivityId) {
        List<HistoricActivityInstance> gateways = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInstId)
                .activityType("parallelGateway")
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
        if (gateways.isEmpty()) {
            return true; // 无并行网关，不受限
        }
        Instant forkTime = gateways.get(0).getStartTime() == null
                ? null : gateways.get(0).getStartTime().toInstant();
        HistoricActivityInstance target = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(procInstId)
                .activityType("userTask")
                .activityId(targetActivityId)
                .finished()
                .orderByHistoricActivityInstanceEndTime().desc()
                .list().stream().findFirst().orElse(null);
        if (target == null || target.getEndTime() == null || forkTime == null) {
            return false;
        }
        return target.getEndTime().toInstant().isBefore(forkTime);
    }

    private String businessKeyOf(String procInstId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult();
        return pi != null ? pi.getBusinessKey() : null;
    }
}
