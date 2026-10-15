package com.cim.approval.server.controller;

import com.cim.approval.server.dto.DefinitionXmlView;
import com.cim.approval.server.dto.DeployProcessRequest;
import com.cim.approval.server.dto.ProcessDefinitionView;
import com.cim.approval.server.dto.ProcessDetailView;
import com.cim.approval.server.dto.StartProcessRequest;
import com.cim.approval.server.service.ApprovalProcessService;
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

@RestController
@RequestMapping("/api/v1/approval")
@RequiredArgsConstructor
public class ProcessController {

    private final ApprovalProcessService processService;

    @GetMapping("/definitions")
    public List<ProcessDefinitionView> listDefinitions() {
        return processService.listDefinitions();
    }

    /** 读取定义 BPMN XML（设计器编辑/查看入口）。 */
    @GetMapping("/processes/{definitionId}/xml")
    public DefinitionXmlView getDefinitionXml(@PathVariable String definitionId) {
        return processService.getDefinitionXml(definitionId);
    }

    @PostMapping("/processes/start")
    public Map<String, String> startProcess(@RequestBody StartProcessRequest req) {
        String piId = processService.startProcess(req);
        return Map.of("processInstanceId", piId);
    }

    /** 审批详情聚合（实例 + 业务表单 + 实时状态 + 审批历史 + 附件）。 */
    @GetMapping("/processes/instances/{instanceId}/detail")
    public ProcessDetailView getDetail(@PathVariable String instanceId, @RequestParam String userId) {
        return processService.getDetail(instanceId, userId);
    }

    /** 撤回：发起人终止运行中实例（仅发起人可操作）。 */
    @PostMapping("/processes/instances/{instanceId}/withdraw")
    public void withdraw(@PathVariable String instanceId, @RequestParam String actor) {
        processService.withdraw(instanceId, actor);
    }

    /** 催办：发起人向当前待办人推送实时催办通知（仅发起人可操作）。 */
    @PostMapping("/processes/instances/{instanceId}/urge")
    public void urge(@PathVariable String instanceId, @RequestParam String actor) {
        processService.urge(instanceId, actor);
    }

    @PostMapping("/processes/deploy")
    public ProcessDefinitionView deployProcess(@RequestBody DeployProcessRequest req) {
        return processService.deployProcess(req);
    }
}
