package com.cim.mds.server.flow;

import java.util.List;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一指派 API（ICM：ContextChain 三 scope 一套端点）。
 *
 * <p>L0 {@code /operations/{opCode}/assigns}（mds:opAssign:*）、
 * L1 {@code /flows/{flowId}/assigns}（mds:flowAssign:*）、
 * L2 {@code /flows/{flowId}/steps/{stepId}/assigns}（mds:stepAssign:*，
 * stepId = node_id）。三组共用 {@link EquipAssignAdminService}。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class EquipAssignController {

    private final EquipAssignAdminService service;

    // ---------------- L0 工序默认指派 ----------------

    @PreAuthorize("hasAuthority('mds:opAssign:view')")
    @GetMapping("/operations/{opCode}/assigns")
    public Result<List<CimEquipAssign>> listByOperation(@PathVariable String opCode) {
        return Result.ok(service.listByOperation(opCode));
    }

    @PreAuthorize("hasAuthority('mds:opAssign:create')")
    @PostMapping("/operations/{opCode}/assigns")
    public Result<CimEquipAssign> createForOperation(
            @PathVariable String opCode, @RequestBody EquipAssignAdminService.AssignReq req) {
        return Result.ok(service.createForOperation(opCode, req));
    }

    // ---------------- L1 流程级覆盖指派 ----------------

    @PreAuthorize("hasAuthority('mds:flowAssign:view')")
    @GetMapping("/flows/{flowId}/assigns")
    public Result<List<CimEquipAssign>> listByFlow(@PathVariable String flowId) {
        return Result.ok(service.listByFlow(flowId));
    }

    @PreAuthorize("hasAuthority('mds:flowAssign:create')")
    @PostMapping("/flows/{flowId}/assigns")
    public Result<CimEquipAssign> createForFlow(
            @PathVariable String flowId, @RequestBody EquipAssignAdminService.AssignReq req) {
        return Result.ok(service.createForFlow(flowId, req));
    }

    // ---------------- L2 节点覆盖指派 ----------------

    @PreAuthorize("hasAuthority('mds:stepAssign:view')")
    @GetMapping("/flows/{flowId}/steps/{stepId}/assigns")
    public Result<List<CimEquipAssign>> listByStep(
            @PathVariable String flowId, @PathVariable String stepId) {
        return Result.ok(service.listByStep(flowId, stepId));
    }

    @PreAuthorize("hasAuthority('mds:stepAssign:create')")
    @PostMapping("/flows/{flowId}/steps/{stepId}/assigns")
    public Result<CimEquipAssign> createForStep(
            @PathVariable String flowId, @PathVariable String stepId,
            @RequestBody EquipAssignAdminService.AssignReq req) {
        return Result.ok(service.createForStep(flowId, stepId, req));
    }

    // ---------------- 通用 UPDATE / DELETE（scope 由行决定，权限码取三组并集最严：update/delete 分组） ----------------

    @PreAuthorize("hasAuthority('mds:opAssign:update') or hasAuthority('mds:flowAssign:update') or hasAuthority('mds:stepAssign:update')")
    @PutMapping("/assigns/{id}")
    public Result<CimEquipAssign> update(
            @PathVariable String id, @RequestBody EquipAssignAdminService.AssignReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:opAssign:delete') or hasAuthority('mds:flowAssign:delete') or hasAuthority('mds:stepAssign:delete')")
    @DeleteMapping("/assigns/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
