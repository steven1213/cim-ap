package com.cim.approval.server.service;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.approval.server.domain.ApprovalAudit;
import com.cim.approval.server.dto.AuditView;
import com.cim.approval.server.dto.CompleteTaskRequest;
import com.cim.approval.server.dto.DefinitionXmlView;
import com.cim.approval.server.dto.DeployProcessRequest;
import com.cim.approval.server.dto.FormSchemaField;
import com.cim.core.shared.PageResult;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                    d.getTenantId(), d.isSuspended()));
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
                pd.getTenantId(), pd.isSuspended());
    }

    /**
     * 发起流程。<b>@Transactional 使 Flowable 命令与 JPA 审计同事务原子提交</b>——
     * 否则审计失败时流程已推进而审计缺失（孤儿实例），哈希链断裂。
     */
    @Transactional
    public String startProcess(StartProcessRequest req) {
        ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(req.processKey())
                .latestVersion().singleResult();
        if (def == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "流程定义不存在: " + req.processKey());
        }
        if (def.isSuspended()) {
            throw new BizException(BizCode.PARAM_INVALID, "流程已停用，无法发起: " + req.processKey());
        }
        validateStartAgainstBpmn(def, req);
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
                String json = objectMapper.writeValueAsString(req.formData());
                if (json.length() > MAX_FORM_JSON_LENGTH) {
                    throw new BizException(BizCode.PARAM_INVALID,
                            "审批内容过大（" + json.length() + " > " + MAX_FORM_JSON_LENGTH + " 字符），请精简或改走附件");
                }
                vars.put("formDataJson", json);
            } catch (BizException e) {
                throw e;
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
        return listTodoPaged(userId, tenantId, 1, Integer.MAX_VALUE).records();
    }

    /**
     * 待办分页查询。<b>批量取实例消除 N+1</b>：任务页取回后一次 processInstanceIds 批查，
     * 而非每任务单独查实例。
     */
    public PageResult<TaskView> listTodoPaged(String userId, String tenantId, int page, int size) {
        int page0 = Math.max(page, 1) - 1; // 平台分页页号 1 起，Flowable listPage 0 起
        var query = taskService.createTaskQuery()
                .taskCandidateOrAssigned(userId)
                .processVariableValueEquals("tenantId", tenantId)
                .active()
                .orderByTaskCreateTime().desc();
        long total = query.count();
        List<Task> tasks = query.listPage(page0 * size, size);
        Map<String, ProcessInstance> pis = instancesOf(tasks);
        List<TaskView> views = new ArrayList<>();
        for (Task t : tasks) {
            ProcessInstance pi = pis.get(t.getProcessInstanceId());
            views.add(toView(t, pi != null ? pi.getBusinessKey() : null,
                    pi != null ? pi.getProcessDefinitionKey() : null,
                    pi != null ? (String) pi.getProcessVariables().get("title") : null,
                    t.getTenantId()));
        }
        return PageResult.of(views, total, page0 + 1, size);
    }

    /** 批量取任务所属流程实例（含流程变量），一次查询替代逐任务查询。 */
    /** formData JSON 上限（字符），超出拒绝发起并提示走附件。 */
    private static final int MAX_FORM_JSON_LENGTH = 65_536;

    /**
     * 发起前按定义 BPMN 做参数匹配校验（400 明确文案，替代运行时 500）：
     * 会签定义引用 {@code ${approvers}} 集合但请求未带 approvers → 400；
     * 定义按 {@code ${assignee}} 取受理人但请求未带 → 400。
     */
    private void validateStartAgainstBpmn(ProcessDefinition def, StartProcessRequest req) {
        String xml;
        try {
            xml = new String(repositoryService.getResourceAsStream(def.getDeploymentId(), def.getResourceName())
                    .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return; // 读不到定义资源时跳过 BPMN 级校验，交由运行时兜底
        }
        boolean needsApprovers = xml.contains("${approvers}");
        boolean needsAssignee = xml.contains("assignee=\"${assignee}\"");
        boolean hasApprovers = req.approvers() != null && !req.approvers().isEmpty();
        boolean hasAssignee = req.assignee() != null && !req.assignee().isBlank();
        if (needsApprovers && !hasApprovers) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "该流程为会签（" + def.getKey() + "），必须提供 approvers 审批人列表");
        }
        if (!needsApprovers && needsAssignee && !hasAssignee && !hasApprovers) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "该流程需要指定审批人（assignee），请填写后重试");
        }
    }


    /** 替换 BPMN &lt;process id=".." name=".."&gt; 开标签属性（复制定义时对齐新 key/name）。 */
    private static String replaceProcessTag(String xml, String newKey, String newName) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<process[^>]*>").matcher(xml);
        if (!m.find()) {
            return xml;
        }
        String tag = m.group();
        String replaced = tag
                .replaceFirst("id=\"[^\"]*\"", "id=\"" + newKey + "\"")
                .replaceFirst("name=\"[^\"]*\"", "name=\"" + newName + "\"");
        if (!replaced.contains("name=")) {
            replaced = replaced.substring(0, replaced.length() - 1) + " name=\"" + newName + "\">";
        }
        return xml.substring(0, m.start()) + replaced + xml.substring(m.end());
    }

    /** 委派：Flowable 原生委派语义——受托人办理后（resolve）任务自动归还原办理人。 */
    @Transactional
    public void delegateTask(String taskId, String toAssignee, String reason, String actor) {
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        if (task == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "任务不存在: " + taskId);
        }
        assertTaskOperator(taskId, actor);
        if (toAssignee == null || toAssignee.isBlank() || toAssignee.equals(actor)) {
            throw new BizException(BizCode.PARAM_INVALID, "受托人不能为空且不能是自己");
        }
        String before = task.getName() + "@" + task.getAssignee();
        taskService.delegateTask(taskId, toAssignee);
        String bk = businessKeyOf(task.getProcessInstanceId());
        auditService.append(bk != null ? bk : task.getProcessInstanceId(),
                task.getProcessInstanceId(), taskId, "DELEGATE", actor, before, toAssignee, reason);
    }

    /** 受托人归还任务（Flowable resolve），任务回到原办理人名下。 */
    @Transactional
    public void resolveTask(String taskId, String comment, String actor) {
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        if (task == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "任务不存在: " + taskId);
        }
        assertTaskOperator(taskId, actor);
        String before = task.getName() + "@" + task.getAssignee();
        taskService.resolveTask(taskId);
        auditService.append(businessKeyOf(task.getProcessInstanceId()), task.getProcessInstanceId(),
                taskId, "RESOLVE", actor, before, task.getOwner() != null ? task.getOwner() : "owner",
                comment);
    }

    /** 按 taskId 批量办理（逐条独立成功/失败，返回逐条结果；任一条失败不回滚其余）。 */
    public List<BatchResult> batchComplete(List<String> taskIds, String outcome, String comment, String actor) {
        List<BatchResult> results = new ArrayList<>();
        for (String taskId : taskIds == null ? List.<String>of() : taskIds) {
            try {
                completeTask(taskId, new CompleteTaskRequest(outcome, comment, actor));
                results.add(new BatchResult(taskId, true, null));
            } catch (Exception e) {
                results.add(new BatchResult(taskId, false, e.getMessage()));
            }
        }
        return results;
    }

    /** 批量办理逐条结果。 */
    public record BatchResult(String taskId, boolean ok, String error) {
    }

    private String businessKeyOf(String processInstanceId) {
        var pi = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        return pi != null ? pi.getBusinessKey() : null;
    }

    /** 定义停用/启用（管理员运维：停用后不可发起新实例）。 */
    public void suspendDefinition(String definitionId, boolean suspend) {
        if (suspend) {
            repositoryService.suspendProcessDefinitionById(definitionId);
        } else {
            repositoryService.activateProcessDefinitionById(definitionId);
        }
    }

    /**
     * 复制定义（原型「复制流程」）：以指定定义为模板，生成 {@code <key>_copy<N>} 新 key 的 v1 副本，
     * 名称追加「（副本）」。复制后进入设计器可改再部署/发布。
     */
    public ProcessDefinitionView duplicateDefinition(String definitionId) {
        ProcessDefinition src = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId).singleResult();
        if (src == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "流程定义不存在: " + definitionId);
        }
        String xml;
        try {
            xml = new String(repositoryService.getResourceAsStream(src.getDeploymentId(), src.getResourceName())
                    .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "读取定义 BPMN 失败: " + e.getMessage());
        }
        // 生成唯一新 key：base_copy / base_copy2 …（避免 key 冲突）
        String newKey = src.getKey() + "_copy";
        int n = 2;
        while (repositoryService.createProcessDefinitionQuery().processDefinitionKey(newKey).count() > 0) {
            newKey = src.getKey() + "_copy" + n++;
        }
        String newName = src.getName() != null ? src.getName() + "（副本）" : newKey;
        // BPMN 内 process 开标签与 key 对齐（部署查询按 key=process id 走）
        xml = replaceProcessTag(xml, newKey, newName);
        org.flowable.engine.repository.Deployment dep = repositoryService.createDeployment()
                .name(newName)
                .addString(newKey + ".bpmn20.xml", xml)
                .deploy();
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .deploymentId(dep.getId()).singleResult();
        if (pd == null) {
            throw new BizException(BizCode.PARAM_INVALID, "复制部署成功但未能解析流程定义");
        }
        return new ProcessDefinitionView(pd.getId(), pd.getKey(), pd.getName(), pd.getVersion(),
                pd.getTenantId(), pd.isSuspended());
    }

    private Map<String, ProcessInstance> instancesOf(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return Map.of();
        }
        Set<String> ids = new HashSet<>();
        for (Task t : tasks) {
            ids.add(t.getProcessInstanceId());
        }
        Map<String, ProcessInstance> map = new HashMap<>();
        runtimeService.createProcessInstanceQuery()
                .processInstanceIds(ids)
                .includeProcessVariables()
                .list()
                .forEach(pi -> map.put(pi.getProcessInstanceId(), pi));
        return map;
    }

    public List<TaskView> listDone(String userId, String tenantId) {
        return listDonePaged(userId, tenantId, 1, Integer.MAX_VALUE).records();
    }

    /** 已办分页查询。 */
    public PageResult<TaskView> listDonePaged(String userId, String tenantId, int page, int size) {
        int page0 = Math.max(page, 1) - 1;
        var query = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(userId)
                .processVariableValueEquals("tenantId", tenantId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc();
        long total = query.count();
        List<HistoricTaskInstance> tasks = query.listPage(page0 * size, size);
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
                    null, null));
        }
        return PageResult.of(views, total, page0 + 1, size);
    }

    public List<ProcessInstanceView> listInitiated(String userId, String tenantId) {
        return listInitiatedPaged(userId, tenantId, 1, Integer.MAX_VALUE).records();
    }

    /**
     * 我发起的分页查询：进行中（通常少量，全取）+ 已结束（listPage 按剩余配额取）。
     * 排序约定：进行中在前，其余按开始时间倒序（与原全量行为一致）。
     */
    public PageResult<ProcessInstanceView> listInitiatedPaged(String userId, String tenantId, int page, int size) {
        int page0 = Math.max(page, 1) - 1;
        List<ProcessInstanceView> runningList = new ArrayList<>();
        runtimeService.createProcessInstanceQuery()
                .variableValueEquals("tenantId", tenantId)
                .variableValueEquals("initiator", userId)
                .includeProcessVariables()
                .list()
                .forEach(pi -> runningList.add(new ProcessInstanceView(pi.getId(), pi.getBusinessKey(),
                        pi.getProcessDefinitionKey(), (String) pi.getProcessVariables().get("title"),
                        pi.getTenantId(), "ACTIVE",
                        toInstant(pi.getStartTime()), null,
                        maxOverdueOf(pi.getId()))));

        var historicQuery = historyService.createHistoricProcessInstanceQuery()
                .variableValueEquals("tenantId", tenantId)
                .variableValueEquals("initiator", userId)
                .finished()
                .includeProcessVariables()
                .orderByProcessInstanceStartTime().desc();
        long historicTotal = historicQuery.count();
        long total = runningList.size() + historicTotal;

        int offset = page0 * size;
        List<ProcessInstanceView> items = new ArrayList<>();
        if (offset < runningList.size()) {
            // 本页含进行中部分
            items.addAll(runningList.subList(offset, (int) Math.min(runningList.size(), (long) offset + size)));
        }
        int historicSkip = (int) Math.max(0, (long) offset - runningList.size());
        int historicLimit = size - items.size();
        if (historicLimit > 0) {
            historicQuery.listPage(historicSkip, historicLimit).forEach(hpi ->
                    items.add(new ProcessInstanceView(hpi.getId(), hpi.getBusinessKey(),
                            hpi.getProcessDefinitionKey(), (String) hpi.getProcessVariables().get("title"),
                            hpi.getTenantId(),
                            hpi.getDeleteReason() != null ? "WITHDRAWN" : "COMPLETED",
                            toInstant(hpi.getStartTime()), toInstant(hpi.getEndTime()),
                            null)));
        }
        return PageResult.of(items, total, page0 + 1, size);
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
    @Transactional
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
        // 撤回策略（原型「允许（首节点未审批前）」）：已产生任一审批票则不可撤回
        if (auditRepository.existsByProcessInstanceIdAndActionStartingWith(instanceId, "COMPLETE_")) {
            throw new BizException(BizCode.PARAM_INVALID, "已有审批人处理该申请，不可撤回；如需终止请联系管理员");
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
    @Transactional
    public void urge(String instanceId, String actor) {
        // 频控：同实例同发起人冷却期内拒绝重复催办（防轰炸；重启清零可接受）
        Instant last = URGE_COOLDOWN.get(instanceId + "|" + actor);
        if (last != null && Duration.between(last, Instant.now()).toMinutes() < urgeCooldownMinutes) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "催办过于频繁，请 " + urgeCooldownMinutes + " 分钟后再试");
        }
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
        URGE_COOLDOWN.put(instanceId + "|" + actor, Instant.now());
    }

    /** 催办冷却记录：实例|发起人 → 最近一次成功催办时间（重启清零可接受）。 */
    private static final Map<String, Instant> URGE_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();

    /** 催办冷却分钟数（同实例同发起人）。 */
    @Value("${approval.urge.cooldown-minutes:5}")
    private double urgeCooldownMinutes;

    @Transactional
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

        if ("REJECT".equals(req.outcome()) && (req.comment() == null || req.comment().isBlank())) {
            throw new BizException(BizCode.PARAM_INVALID, "驳回时必须填写审批意见");
        }
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

        assertInstanceVisible(instanceId, userId);

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

    /**
     * 实例可见性（详情/附件共用）：发起人 ∪ 审计参与人 ∪ 历史任务受理人 ∪ 当前候选/受理人。
     */
    public void assertInstanceVisible(String instanceId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException(BizCode.ACCESS_DENIED, "无权查看该审批");
        }
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId).includeProcessVariables().singleResult();
        if (hpi == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        Map<String, Object> vars = hpi.getProcessVariables() != null ? hpi.getProcessVariables() : Map.of();
        boolean allowed = userId.equals(vars.get("initiator"))
                || auditRepository.existsByProcessInstanceIdAndActor(instanceId, userId)
                || historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(instanceId).taskAssignee(userId).count() > 0
                || taskService.createTaskQuery()
                .processInstanceId(instanceId).taskCandidateOrAssigned(userId).count() > 0;
        if (!allowed) {
            throw new BizException(BizCode.ACCESS_DENIED, "无权查看该审批详情");
        }
    }

    /**
     * 任务办理授权：仅当前任务的受理人或候选人可执行办理/回退/加签/改派。
     * 认证态下 actor 已来自 JWT，杜绝伪造身份代办。
     */
    public void assertTaskOperator(String taskId, String userId) {
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        if (task == null) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "任务不存在或已办理: " + taskId);
        }
        boolean assignee = userId != null && userId.equals(task.getAssignee());
        boolean candidate = !assignee && userId != null && taskService.createTaskQuery()
                .taskId(taskId).taskCandidateUser(userId).active().count() > 0;
        if (!assignee && !candidate) {
            throw new BizException(BizCode.ACCESS_DENIED, "只有该任务的办理人或候选人可以执行此操作");
        }
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
                overdueMinutes(toInstant(t.getCreateTime())),
                t.getDelegationState() != null ? t.getDelegationState().name() : null);
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
