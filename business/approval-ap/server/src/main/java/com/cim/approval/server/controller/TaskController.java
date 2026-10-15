package com.cim.approval.server.controller;

import com.cim.approval.server.dto.AddSignRequest;
import com.cim.approval.server.dto.CompleteTaskRequest;
import com.cim.core.shared.PageResult;
import com.cim.approval.server.dto.ProcessInstanceView;
import com.cim.approval.server.dto.ReassignRequest;
import com.cim.approval.server.dto.ReturnRequest;
import com.cim.approval.server.dto.TaskView;
import com.cim.approval.server.security.CurrentUserResolver;
import com.cim.approval.server.service.ApprovalAddSignService;
import com.cim.approval.server.service.ApprovalProcessService;
import com.cim.approval.server.service.ApprovalReassignService;
import com.cim.approval.server.service.ApprovalReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cim.spring.support.web.Result;

import java.util.List;
import java.util.Map;

/**
 * 任务接口。<b>授权约定</b>：认证态（cim.auth.enabled=true）操作者身份强制取自 JWT，
 * 请求体里的 actor 被忽略；办理/回退/加签/改派/委派统一过 {@code assertTaskOperator}
 * （仅当前任务受理人/候选人可操作）。
 */
@RestController
@RequestMapping("/api/v1/approval/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final ApprovalProcessService processService;
    private final ApprovalReturnService returnService;
    private final ApprovalAddSignService addSignService;
    private final ApprovalReassignService reassignService;
    private final CurrentUserResolver currentUser;

    @GetMapping("/todo")
    public Result<List<TaskView>> todo(@RequestParam(required = false) String userId,
                                       @RequestParam(defaultValue = "default") String tenantId) {
        return Result.ok(processService.listTodo(currentUser.resolve(userId), tenantId));
    }

    @GetMapping("/done")
    public Result<List<TaskView>> done(@RequestParam(required = false) String userId,
                                       @RequestParam(defaultValue = "default") String tenantId) {
        return Result.ok(processService.listDone(currentUser.resolve(userId), tenantId));
    }

    @GetMapping("/initiated")
    public Result<List<ProcessInstanceView>> initiated(@RequestParam(required = false) String userId,
                                                       @RequestParam(defaultValue = "default") String tenantId) {
        return Result.ok(processService.listInitiated(currentUser.resolve(userId), tenantId));
    }

    // ---------- 分页端点（列表页使用；消除全量加载与 N+1） ----------

    @GetMapping("/todo/page")
    public Result<PageResult<TaskView>> todoPaged(@RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "default") String tenantId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(processService.listTodoPaged(
                currentUser.resolve(userId), tenantId, Math.max(page, 1), Math.min(size, 100)));
    }

    @GetMapping("/done/page")
    public Result<PageResult<TaskView>> donePaged(@RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "default") String tenantId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(processService.listDonePaged(
                currentUser.resolve(userId), tenantId, Math.max(page, 1), Math.min(size, 100)));
    }

    @GetMapping("/initiated/page")
    public Result<PageResult<ProcessInstanceView>> initiatedPaged(@RequestParam(required = false) String userId,
                                                                  @RequestParam(defaultValue = "default") String tenantId,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.ok(processService.listInitiatedPaged(
                currentUser.resolve(userId), tenantId, Math.max(page, 1), Math.min(size, 100)));
    }

    @PostMapping("/{taskId}/complete")
    public Result<Void> complete(@PathVariable String taskId, @RequestBody CompleteTaskRequest req) {
        String actor = currentUser.resolve(req.actor());
        processService.assertTaskOperator(taskId, actor);
        processService.completeTask(taskId, new CompleteTaskRequest(req.outcome(), req.comment(), actor));
        return Result.ok();
    }

    /** 批量办理：逐条独立成功/失败，返回逐条结果。 */
    @PostMapping("/batch-complete")
    public Result<List<ApprovalProcessService.BatchResult>> batchComplete(@RequestBody Map<String, Object> body) {
        String actor = currentUser.resolve((String) body.get("actor"));
        @SuppressWarnings("unchecked")
        List<String> taskIds = (List<String>) body.get("taskIds");
        String outcome = String.valueOf(body.getOrDefault("outcome", "APPROVE"));
        String comment = (String) body.get("comment");
        if (!"APPROVE".equals(outcome)) {
            throw new com.cim.spring.support.web.BizException(
                    com.cim.spring.support.web.BizCode.PARAM_INVALID, "批量操作仅支持通过（驳回请逐条填写意见）");
        }
        return Result.ok(processService.batchComplete(taskIds, outcome, comment, actor));
    }

    /** 委派：任务转交受托人办理，受托人 resolve 后归还。 */
    @PostMapping("/{taskId}/delegate")
    public Result<Void> delegate(@PathVariable String taskId, @RequestBody Map<String, String> body) {
        String actor = currentUser.resolve(body.get("actor"));
        processService.delegateTask(taskId, body.get("assignee"), body.get("reason"), actor);
        return Result.ok();
    }

    /** 受托人归还任务（resolve），回到原办理人名下。 */
    @PostMapping("/{taskId}/resolve")
    public Result<Void> resolve(@PathVariable String taskId, @RequestBody Map<String, String> body) {
        String actor = currentUser.resolve(body.get("actor"));
        processService.resolveTask(taskId, body.get("comment"), actor);
        return Result.ok();
    }

    @PostMapping("/{taskId}/return")
    public Result<Void> returnTask(@PathVariable String taskId, @RequestBody ReturnRequest req) {
        String actor = currentUser.resolve(req.actor());
        processService.assertTaskOperator(taskId, actor);
        returnService.returnTask(taskId, new ReturnRequest(req.targetActivityId(), req.reason(), actor));
        return Result.ok();
    }

    @PostMapping("/{taskId}/add-sign")
    public Result<Void> addSign(@PathVariable String taskId, @RequestBody AddSignRequest req) {
        String actor = currentUser.resolve(req.actor());
        processService.assertTaskOperator(taskId, actor);
        addSignService.addSign(taskId, new AddSignRequest(req.assignee(), req.type(), req.reason(), actor));
        return Result.ok();
    }

    @PostMapping("/{taskId}/reassign")
    public Result<Void> reassign(@PathVariable String taskId, @RequestBody ReassignRequest req) {
        String actor = currentUser.resolve(req.actor());
        processService.assertTaskOperator(taskId, actor);
        reassignService.reassign(taskId, new ReassignRequest(req.newAssignee(), req.reason(), actor));
        return Result.ok();
    }
}
