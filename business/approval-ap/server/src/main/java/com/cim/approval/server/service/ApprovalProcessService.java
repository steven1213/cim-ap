package com.cim.approval.server.service;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.approval.server.domain.ApprovalAudit;
import com.cim.approval.server.dto.AuditView;
import com.cim.approval.server.dto.CompleteTaskRequest;
import com.cim.approval.server.dto.DefinitionXmlView;
import com.cim.approval.server.dto.DeployProcessRequest;
import com.cim.approval.server.dto.FormSchemaField;
import com.cim.approval.server.dto.ProcessDefinitionView;
import com.cim.approval.server.dto.ProcessDetailView;
import com.cim.approval.server.dto.ProcessInstanceView;
import com.cim.approval.server.dto.StartProcessRequest;
import com.cim.approval.server.dto.TaskView;
import com.cim.approval.server.repository.ApprovalAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.history.HistoricIdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程引擎核心服务：部署查询、发起、任务查询、审批完成。
 */
@Service
@RequiredArgsConstructor
public class ApprovalProcessService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final AuditService auditService;
    private final CallbackService callbackService;
    private final NotificationService notificationService;
    private final AttachmentService attachmentService;
    private final ApprovalAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    /** 任务超时阈值（分钟，支持小数）：超出后列表标红并由 {@link OverdueScanner} 推送提醒，默认 24h。 */
    @Value("${approval.task.timeout-minutes:1440}")
    private double timeoutMinutes;

    /**
     * 全部版本的定义列表（同 key 按 version 降序）。
     * <p>前端以「同 key 最新版本 = 可编辑、历史版本 = 只可查看」呈现状态。</p>
     */
    public List<ProcessDefinitionView> listDefinitions() {
        List<ProcessDefinition> defs = repositoryService.createProcessDefinitionQuery()
                .orderByProcessDefinitionKey().asc()
                .orderByProcessDefinitionVersion().desc()
                .list();
        List<ProcessDefinitionView> views = new ArrayList<>();
        for (ProcessDefinition d : defs) {
            views.add(new ProcessDefinitionView(d.getId(), d.getKey(), d.getName(), d.getVersion(),
                    d.getTenantId()));
        }
        return views;
    }

    /** 读取指定定义的 BPMN XML（设计器「编辑/查看已有模板」入口）。 */
    public DefinitionXmlView getDefinitionXml(String definitionId) {
        ProcessDefinition d = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId).singleResult();
        if (d == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        try (java.io.InputStream in = repositoryService.getResourceAsStream(d.getDeploymentId(), d.getResourceName())) {
            String xml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return new DefinitionXmlView(d.getId(), d.getKey(), d.getName(), d.getVersion(), xml);
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "读取 BPMN 资源失败: " + e.getMessage());
        }
    }

    /**
     * 运行时部署：前端拖拽设计器导出的 BPMN 2.0 XML 落地为流程定义。
     * <p>同 key 重复部署自动递增版本；部署为全局（Flowable tenant 留空，与 classpath
     * 静态定义一致），实例级租户经 {@code tenantId} 流程变量承载。</p>
     */
    public ProcessDefinitionView deployProcess(DeployProcessRequest req) {
        if (req.bpmnXml() == null || req.bpmnXml().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "bpmnXml 不能为空");
        }
        String key = req.processKey();
        if (key == null || key.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "processKey 不能为空");
        }
        org.flowable.engine.repository.Deployment dep = repositoryService.createDeployment()
                .addString(key + ".bpmn20.xml", req.bpmnXml())
                .name(req.processName() == null ? key : req.processName())
                .deploy();
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(dep.getId()).latestVersion().singleResult();
        if (pd == null) {
            throw new BizException(BizCode.PARAM_INVALID, "部署成功但未能解析流程定义，请检查 BPMN 合法性");
        }
        return new ProcessDefinitionView(pd.getId(), pd.getKey(), pd.getName(), pd.getVersion(),
                pd.getTenantId());
    }

    public String startProcess(StartProcessRequest req) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(req.processKey())
                .latestVersion().singleResult();
        if (def == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "流程定义不存在: " + req.processKey());
        }
        Map<String, Object> vars = new HashMap<>();
        if (req.approvers() != null && !req.approvers().isEmpty()) {
            vars.put("approvers", req.approvers());
            vars.put("approvedCount", 0);
            vars.put("rejectedCount", 0);
            vars.put("approvalThreshold", (int) Math.ceil(req.approvers().size() / 2.0));
        } else if (req.assignee() != null && !req.assignee().isBlank()) {
            vars.put("assignee", req.assignee());
        } else if (req.variables() != null && !req.variables().isEmpty()) {
            // 由流程定义自行使用传入变量（如两级审批的 leader/manager）
        } else {
            throw new BizException(BizCode.PARAM_INVALID, "approvers 与 assignee 至少提供一个，或通过 variables 传入流程变量");
        }
        if (req.title() != null) {
            vars.put("title", req.title());
        }
        if (req.formData() != null && !req.formData().isEmpty()) {
            // 业务表单数据序列化为 JSON 字符串变量，随实例持久化（历史查询也能取回）
            try {
                vars.put("formDataJson", objectMapper.writeValueAsString(req.formData()));
            } catch (Exception ignored) {
                // 序列化失败不阻塞发起，详情页回退展示普通变量
            }
        }
        if (req.formSchema() != null && !req.formSchema().isEmpty()) {
            // 表单字段描述随 formData 一同持久化，详情页据此渲染标签/分组/类型
            try {
                vars.put("formSchemaJson", objectMapper.writeValueAsString(req.formSchema()));
            } catch (Exception ignored) {
                // 同上，缺 schema 时详情页回退为纯键值展示
            }
        }
        if (req.initiator() != null) {
            vars.put("initiator", req.initiator());
        }
        if (req.variables() != null) {
            vars.putAll(req.variables());
        }
        // 回调路由键（tenantId 即业务系统 appCode）
        vars.put("appCode", req.tenantId());
        // 流程实例级 Flowable 租户留空（定义全局共享），租户以变量承载，
        // 查询按 tenantId 变量过滤，实现多业务系统隔离；真正的定义级租户隔离为后续增强。
        vars.put("tenantId", req.tenantId());

        ProcessInstance pi = runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(req.processKey())
                .businessKey(req.businessKey())
                .variables(vars)
                .start();

        auditService.append(req.businessKey(), pi.getProcessInstanceId(), null, "START",
                req.initiator(), null, "RUNNING",
                "title=" + req.title());
        return pi.getProcessInstanceId();
    }

    public List<TaskView> listTodo(String userId, String tenantId) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskCandidateOrAssigned(userId)
                .processVariableValueEquals("tenantId", tenantId)
                .active()
                .orderByTaskCreateTime().desc()
                .list();
        List<TaskView> views = new ArrayList<>();
        for (Task t : tasks) {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(t.getProcessInstanceId())
                    .includeProcessVariables().singleResult();
            String title = pi != null ? (String) pi.getProcessVariables().get("title") : null;
            views.add(toView(t, pi != null ? pi.getBusinessKey() : null,
                    pi != null ? pi.getProcessDefinitionKey() : null, title,
                    t.getTenantId()));
        }
        return views;
    }

    public List<TaskView> listDone(String userId, String tenantId) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(userId)
                .processVariableValueEquals("tenantId", tenantId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
        List<TaskView> views = new ArrayList<>();
        for (HistoricTaskInstance t : tasks) {
            HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(t.getProcessInstanceId())
                    .includeProcessVariables().singleResult();
            String title = hpi != null ? (String) hpi.getProcessVariables().get("title") : null;
            views.add(new TaskView(t.getId(), t.getProcessInstanceId(),
                    hpi != null ? hpi.getProcessDefinitionKey() : null,
                    hpi != null ? hpi.getBusinessKey() : null,
                    title,
                    t.getTenantId(), t.getName(), t.getAssignee(),
                    historicCandidateUsersOf(t.getId()), null,
                    toInstant(t.getCreateTime()), toInstant(t.getEndTime()),
                    null));
        }
        return views;
    }

    public List<ProcessInstanceView> listInitiated(String userId, String tenantId) {
        List<ProcessInstanceView> result = new ArrayList<>();
        runtimeService.createProcessInstanceQuery()
                .variableValueEquals("tenantId", tenantId)
                .variableValueEquals("initiator", userId)
                .includeProcessVariables()
                .list()
                .forEach(pi -> result.add(new ProcessInstanceView(pi.getId(), pi.getBusinessKey(),
                        pi.getProcessDefinitionKey(), (String) pi.getProcessVariables().get("title"),
                        pi.getTenantId(), "ACTIVE",
                        toInstant(pi.getStartTime()), null,
                        maxOverdueOf(pi.getId()))));
        historyService.createHistoricProcessInstanceQuery()
                .variableValueEquals("tenantId", tenantId)
                .variableValueEquals("initiator", userId)
                .finished()
                .includeProcessVariables()
                .list()
                .forEach(hpi -> result.add(new ProcessInstanceView(hpi.getId(), hpi.getBusinessKey(),
                        hpi.getProcessDefinitionKey(), (String) hpi.getProcessVariables().get("title"),
                        hpi.getTenantId(),
                        hpi.getDeleteReason() != null ? "WITHDRAWN" : "COMPLETED",
                        toInstant(hpi.getStartTime()), toInstant(hpi.getEndTime()),
                        null)));
        return result;
    }

    /** 实例当前活动任务中的最大超时分钟数；未超时/无活动任务返回 null。 */
    private Double maxOverdueOf(String processInstanceId) {
        Double max = null;
        for (Task t : taskService.createTaskQuery().processInstanceId(processInstanceId).active().list()) {
            Double m = overdueMinutes(toInstant(t.getCreateTime()));
            if (m != null) {
                max = max == null ? m : Math.max(max, m);
            }
        }
        return max;
    }

    /** 任务创建时间距超时阈值超出的分钟数（小数）；未超时返回 null。 */
    private Double overdueMinutes(Instant createdAt) {
        if (createdAt == null) {
            return null;
        }
        long millis = java.time.Duration.between(createdAt, Instant.now()).toMillis();
        return millis >= timeoutMinutes * 60_000 ? millis / 60_000.0 : null;
    }

    /**
     * 撤回：发起人终止仍在运行中的实例（首个审批节点尚未处理完也可撤，已结束则拒绝）。
     * <p>写入 WITHDRAW 审计并回调业务系统 result=WITHDRAW， HistoricProcessInstance
     * 以 deleteReason=WITHDRAW 落历史，列表据此展示「已撤回」。</p>
     */
    public void withdraw(String instanceId, String actor) {
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).includeProcessVariables().singleResult();
        if (hpi == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        String initiator = (String) (hpi.getProcessVariables() != null
                ? hpi.getProcessVariables().get("initiator") : null);
        if (actor == null || !actor.equals(initiator)) {
            throw new BizException(BizCode.ACCESS_DENIED, "只有发起人可以撤回该审批");
        }
        if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count() == 0) {
            throw new BizException(BizCode.PARAM_INVALID, "流程已结束，无法撤回");
        }
        String businessKey = hpi.getBusinessKey();
        String tenant = String.valueOf(
                hpi.getProcessVariables() != null ? hpi.getProcessVariables().get("tenantId") : null);

        runtimeService.deleteProcessInstance(instanceId, "WITHDRAW");

        auditService.append(businessKey, instanceId, null, "WITHDRAW", actor,
                "RUNNING", "WITHDRAW", "title=" + hpi.getProcessVariables().get("title"));
        callbackService.enqueueOnComplete(instanceId, businessKey, tenant, "WITHDRAW");
    }

    /**
     * 催办：发起人向当前待办任务的办理人/候选人推送实时催办通知（SSE），并记 URGE 审计。
     */
    public void urge(String instanceId, String actor) {
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).includeProcessVariables().singleResult();
        if (hpi == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        String initiator = (String) (hpi.getProcessVariables() != null
                ? hpi.getProcessVariables().get("initiator") : null);
        if (actor == null || !actor.equals(initiator)) {
            throw new BizException(BizCode.ACCESS_DENIED, "只有发起人可以催办该审批");
        }
        if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count() == 0) {
            throw new BizException(BizCode.PARAM_INVALID, "流程已结束，无需催办");
        }
        String businessKey = hpi.getBusinessKey();
        String title = String.valueOf(hpi.getProcessVariables().get("title"));
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(instanceId).active()
                .orderByTaskCreateTime().asc().list();

        java.util.Set<String> notified = new java.util.HashSet<>();
        for (Task t : tasks) {
            if (t.getAssignee() != null && !t.getAssignee().isBlank()) {
                notified.add(t.getAssignee());
                notificationService.pushToUser(t.getAssignee(), "URGE", Map.of(
                        "type", "URGE",
                        "taskId", t.getId(),
                        "name", t.getName(),
                        "processInstanceId", instanceId,
                        "businessKey", businessKey,
                        "title", title,
                        "from", actor));
            }
            for (String candidate : candidateUsersOf(t.getId())) {
                if (notified.add(candidate)) {
                    notificationService.pushToUser(candidate, "URGE", Map.of(
                            "type", "URGE",
                            "taskId", t.getId(),
                            "name", t.getName(),
                            "processInstanceId", instanceId,
                            "businessKey", businessKey,
                            "title", title,
                            "from", actor));
                }
            }
        }
        auditService.append(businessKey, instanceId, null, "URGE", actor,
                null, null, "urgeTargets=" + String.join(",", notified));
    }

    public void completeTask(String taskId, CompleteTaskRequest req) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "任务不存在: " + taskId);
        }
        String procInstId = task.getProcessInstanceId();
        String businessKey = runtimeService.createProcessInstanceQuery()
                .processInstanceId(procInstId).singleResult().getBusinessKey();
        // 必须在 complete 之前读取：最后一票审批会使流程实例结束、execution 被删除，
        // 之后再 getVariable 会抛 FlowableObjectNotFoundException。
        String tenant = (String) runtimeService.getVariable(procInstId, "tenantId");
        String before = task.getName() + "@" + task.getAssignee();

        Map<String, Object> vars = new HashMap<>();
        vars.put("outcome", req.outcome());
        if (req.comment() != null) {
            vars.put("comment", req.comment());
        }
        taskService.complete(task.getId(), vars);

        String action = "APPROVE".equals(req.outcome()) ? "COMPLETE_APPROVE" : "COMPLETE_REJECT";
        String detail;
        try {
            detail = objectMapper.writeValueAsString(Map.of("comment", req.comment()));
        } catch (Exception e) {
            detail = "comment=" + req.comment();
        }
        auditService.append(businessKey, procInstId, task.getId(), action, req.actor(), before,
                req.outcome(), detail);

        long remaining = runtimeService.createProcessInstanceQuery().processInstanceId(procInstId).count();
        if (remaining == 0) {
            String result = readResult(procInstId);
            callbackService.enqueueOnComplete(procInstId, businessKey, tenant, result);
        }
    }

    /**
     * 审批详情聚合：实例信息 + 业务表单数据 + 实时活动节点/待办 + 审批历史 + 附件。
     * <p>可见性：发起人、审计参与人（审批/回退/加签等动作 actor）、历史任务受理人、
     * 当前任务候选/受理人。</p>
     */
    public ProcessDetailView getDetail(String instanceId, String userId) {
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).includeProcessVariables().singleResult();
        if (hpi == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        boolean running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId).count() > 0;
        Map<String, Object> vars = hpi.getProcessVariables() != null ? hpi.getProcessVariables() : Map.of();
        String initiator = (String) vars.get("initiator");

        boolean allowed = userId != null && !userId.isBlank() && (
                userId.equals(initiator)
                        || auditRepository.existsByProcessInstanceIdAndActor(instanceId, userId)
                        || historyService.createHistoricTaskInstanceQuery()
                        .processInstanceId(instanceId).taskAssignee(userId).count() > 0
                        || taskService.createTaskQuery()
                        .processInstanceId(instanceId).taskCandidateOrAssigned(userId).count() > 0);
        if (!allowed) {
            throw new BizException(BizCode.ACCESS_DENIED, "无权查看该审批详情");
        }

        String businessKey = hpi.getBusinessKey();
        Map<String, Object> formData = parseFormData(vars.get("formDataJson"));
        List<FormSchemaField> formSchema = parseFormSchema(vars.get("formSchemaJson"));

        List<TaskView> activeTasks = new ArrayList<>();
        List<String> activeNodes = new ArrayList<>();
        if (running) {
            ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(instanceId).singleResult();
            String title = (String) vars.get("title");
            for (Task t : taskService.createTaskQuery()
                    .processInstanceId(instanceId).active()
                    .orderByTaskCreateTime().asc().list()) {
                activeTasks.add(toView(t, businessKey, pi.getProcessDefinitionKey(), title, t.getTenantId()));
            }
            try {
                List<String> ids = runtimeService.getActiveActivityIds(instanceId);
                if (ids != null) {
                    activeNodes = ids;
                }
            } catch (Exception ignored) {
                // 实例刚结束时可能取不到，忽略
            }
        }

        String definitionName = null;
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(hpi.getProcessDefinitionId()).singleResult();
        if (def != null) {
            definitionName = def.getName();
        }

        List<AuditView> history = new ArrayList<>();
        for (ApprovalAudit a : auditService.listByBusinessKey(businessKey)) {
            history.add(new AuditView(a.getId(), a.getSeq(), a.getBusinessKey(), a.getProcessInstanceId(),
                    a.getTaskId(), a.getAction(), a.getActor(), a.getBeforeState(), a.getAfterState(),
                    a.getDetail(), a.getPrevHash(), a.getHash(), a.getCreatedAt()));
        }

        return new ProcessDetailView(instanceId, businessKey, hpi.getProcessDefinitionKey(), definitionName,
                (String) vars.get("title"), initiator, (String) vars.get("tenantId"),
                running ? "ACTIVE" : (hpi.getDeleteReason() != null ? "WITHDRAWN" : "COMPLETED"),
                toInstant(hpi.getStartTime()), toInstant(hpi.getEndTime()),
                formData, formSchema, activeNodes, activeTasks, history,
                attachmentService.listByBusinessKey(businessKey));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFormData(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<FormSchemaField> parseFormSchema(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    String.valueOf(raw),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, FormSchemaField.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String readResult(String processInstanceId) {        return historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName("result")
                .singleResult() != null
                ? String.valueOf(historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId).variableName("result").singleResult().getValue())
                : "UNKNOWN";
    }

    private TaskView toView(Task t, String businessKey, String processKey, String title, String tenantId) {
        return new TaskView(t.getId(), t.getProcessInstanceId(), processKey, businessKey, title, tenantId,
                t.getName(), t.getAssignee(), candidateUsersOf(t.getId()),
                (String) t.getTaskLocalVariables().get("outcome"),
                toInstant(t.getCreateTime()), toInstant(t.getDueDate()),
                overdueMinutes(toInstant(t.getCreateTime())));
    }

    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private List<String> candidateUsersOf(String taskId) {
        List<String> users = new ArrayList<>();
        for (IdentityLink link : taskService.getIdentityLinksForTask(taskId)) {
            if ("candidate".equals(link.getType()) && link.getUserId() != null) {
                users.add(link.getUserId());
            }
        }
        return users;
    }

    private List<String> historicCandidateUsersOf(String taskId) {
        List<String> users = new ArrayList<>();
        for (HistoricIdentityLink link : historyService.getHistoricIdentityLinksForTask(taskId)) {
            if ("candidate".equals(link.getType()) && link.getUserId() != null) {
                users.add(link.getUserId());
            }
        }
        return users;
    }
}
