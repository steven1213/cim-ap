package com.cim.approval.server.controller;

import com.cim.approval.server.dto.DefinitionXmlView;
import com.cim.approval.server.dto.DeployProcessRequest;
import com.cim.approval.server.dto.ProcessDefinitionView;
import com.cim.approval.server.dto.ProcessDetailView;
import com.cim.approval.server.dto.StartProcessRequest;
import com.cim.approval.server.dto.StatsView;
import com.cim.approval.server.security.CurrentUserResolver;
import com.cim.approval.server.service.ApprovalProcessService;
import com.cim.approval.server.service.StatsService;
import com.cim.core.shared.PageResult;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 流程接口。响应统一走平台 {@link Result} 封装（platform README §4）；
 * 分页走 {@link PageResult}（records/total/page/size，页号从 1 开始）。
 */
@RestController
@RequestMapping("/api/v1/approval")
@RequiredArgsConstructor
public class ProcessController {

    private final ApprovalProcessService processService;
    private final StatsService statsService;
    private final CurrentUserResolver currentUser;

    @GetMapping("/definitions")
    public Result<List<ProcessDefinitionView>> listDefinitions() {
        return Result.ok(processService.listDefinitions());
    }

    /** 读取定义 BPMN XML（设计器编辑/查看入口）。 */
    @GetMapping("/processes/{definitionId}/xml")
    public Result<DefinitionXmlView> getDefinitionXml(@PathVariable String definitionId) {
        return Result.ok(processService.getDefinitionXml(definitionId));
    }

    @PostMapping("/processes/start")
    public Result<Map<String, String>> startProcess(@RequestBody StartProcessRequest req) {
        // 发起人身份强制取可信来源：认证态=JWT（忽略请求体 initiator），dev 态=请求体
        StartProcessRequest safe = new StartProcessRequest(req.processKey(), req.businessKey(),
                req.tenantId(), currentUser.resolve(req.initiator()), req.title(),
                req.approvers(), req.assignee(), req.variables(), req.formData(), req.formSchema());
        String piId = processService.startProcess(safe);
        return Result.ok(Map.of("processInstanceId", piId));
    }

    /** 复制定义：以指定定义为模板，生成新 key 的 v1 副本（原型「复制流程」）。 */
    @PostMapping("/processes/{definitionId}/duplicate")
    public Result<ProcessDefinitionView> duplicate(@PathVariable String definitionId) {
        return Result.ok(processService.duplicateDefinition(definitionId));
    }

    /** 审批详情聚合（实例 + 业务表单 + 实时状态 + 审批历史 + 附件）。可见性服务端强制。 */
    @GetMapping("/processes/instances/{instanceId}/detail")
    public Result<ProcessDetailView> getDetail(@PathVariable String instanceId,
                                               @RequestParam(required = false) String userId) {
        return Result.ok(processService.getDetail(instanceId, currentUser.resolve(userId)));
    }

    /**
     * 撤回：发起人终止运行中实例。
     * 策略（对齐原型「允许（首节点未审批前）」）：任一审批票已产生则不可撤回。
     */
    @PostMapping("/processes/instances/{instanceId}/withdraw")
    public Result<Void> withdraw(@PathVariable String instanceId, @RequestParam(required = false) String actor) {
        processService.withdraw(instanceId, currentUser.resolve(actor));
        return Result.ok();
    }

    /** 催办：发起人向当前待办人推送实时催办通知（仅发起人可操作；含冷却频控）。 */
    @PostMapping("/processes/instances/{instanceId}/urge")
    public Result<Void> urge(@PathVariable String instanceId, @RequestParam(required = false) String actor) {
        processService.urge(instanceId, currentUser.resolve(actor));
        return Result.ok();
    }

    @PostMapping("/processes/deploy")
    public Result<ProcessDefinitionView> deployProcess(@RequestBody DeployProcessRequest req) {
        return Result.ok(processService.deployProcess(req));
    }

    /** 我发起的分页查询（page 从 1 开始）。 */
    @GetMapping("/processes/initiated/page")
    public Result<PageResult<com.cim.approval.server.dto.ProcessInstanceView>> initiatedPaged(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(processService.listInitiatedPaged(
                currentUser.resolve(userId), tenantId, Math.max(page, 1), Math.min(size, 100)));
    }

    /** 审批效率统计（Dashboard 看板）。 */
    @GetMapping("/stats")
    public Result<StatsView> stats(@RequestParam(defaultValue = "default") String tenantId,
                                   @RequestParam(defaultValue = "30") int days) {
        return Result.ok(statsService.stats(tenantId, Math.min(Math.max(days, 1), 90)));
    }

    /** 定义停用/启用（管理员运维：停用后不可发起新实例）。 */
    @PostMapping("/processes/{definitionId}/suspend")
    public Result<Void> suspend(@PathVariable String definitionId, @RequestParam(defaultValue = "true") boolean suspend) {
        processService.suspendDefinition(definitionId, suspend);
        return Result.ok();
    }
}
