package com.cim.approval.server.controller;

import com.cim.approval.server.dto.AddSignRequest;
import com.cim.approval.server.dto.CompleteTaskRequest;
import com.cim.approval.server.dto.ProcessInstanceView;
import com.cim.approval.server.dto.ReassignRequest;
import com.cim.approval.server.dto.ReturnRequest;
import com.cim.approval.server.dto.TaskView;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/approval/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final ApprovalProcessService processService;
    private final ApprovalReturnService returnService;
    private final ApprovalAddSignService addSignService;
    private final ApprovalReassignService reassignService;

    @GetMapping("/todo")
    public List<TaskView> todo(@RequestParam String userId, @RequestParam(defaultValue = "default") String tenantId) {
        return processService.listTodo(userId, tenantId);
    }

    @GetMapping("/done")
    public List<TaskView> done(@RequestParam String userId, @RequestParam(defaultValue = "default") String tenantId) {
        return processService.listDone(userId, tenantId);
    }

    @GetMapping("/initiated")
    public List<ProcessInstanceView> initiated(@RequestParam String userId, @RequestParam(defaultValue = "default") String tenantId) {
        return processService.listInitiated(userId, tenantId);
    }

    @PostMapping("/{taskId}/complete")
    public void complete(@PathVariable String taskId, @RequestBody CompleteTaskRequest req) {
        processService.completeTask(taskId, req);
    }

    @PostMapping("/{taskId}/return")
    public void returnTask(@PathVariable String taskId, @RequestBody ReturnRequest req) {
        returnService.returnTask(taskId, req);
    }

    @PostMapping("/{taskId}/add-sign")
    public void addSign(@PathVariable String taskId, @RequestBody AddSignRequest req) {
        addSignService.addSign(taskId, req);
    }

    @PostMapping("/{taskId}/reassign")
    public void reassign(@PathVariable String taskId, @RequestBody ReassignRequest req) {
        reassignService.reassign(taskId, req);
    }
}
