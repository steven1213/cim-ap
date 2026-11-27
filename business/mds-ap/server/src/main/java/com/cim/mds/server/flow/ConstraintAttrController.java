package com.cim.mds.server.flow;

import java.util.Map;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * KV 约束参数 API（ICM：context 三层一套批量 upsert，PUT = 全量快照）。
 *
 * <p>L0 {@code /operations/{opCode}/attrs}（mds:opAttr:*）、
 * L1 {@code /flows/{flowId}/attrs}（mds:flowAttr:*）、
 * L2 {@code /flows/{flowId}/steps/{stepId}/attrs}（mds:stepAttr:*，stepId = node_id）。
 * 三层共用 {@link ConstraintAttrAdminService}。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ConstraintAttrController {

    private final ConstraintAttrAdminService service;

    @PreAuthorize("hasAuthority('mds:opAttr:view')")
    @GetMapping("/operations/{opCode}/attrs")
    public Result<Map<String, String>> listByOperation(@PathVariable String opCode) {
        return Result.ok(service.listByOperation(opCode));
    }

    @PreAuthorize("hasAuthority('mds:opAttr:update')")
    @PutMapping("/operations/{opCode}/attrs")
    public Result<Map<String, String>> putForOperation(
            @PathVariable String opCode,
            @RequestBody ConstraintAttrAdminService.AttrPutReq req) {
        return Result.ok(service.putForOperation(opCode, req.values()));
    }

    @PreAuthorize("hasAuthority('mds:flowAttr:view')")
    @GetMapping("/flows/{flowId}/attrs")
    public Result<Map<String, String>> listByFlow(@PathVariable String flowId) {
        return Result.ok(service.listByFlow(flowId));
    }

    @PreAuthorize("hasAuthority('mds:flowAttr:update')")
    @PutMapping("/flows/{flowId}/attrs")
    public Result<Map<String, String>> putForFlow(
            @PathVariable String flowId,
            @RequestBody ConstraintAttrAdminService.AttrPutReq req) {
        return Result.ok(service.putForFlow(flowId, req));
    }

    @PreAuthorize("hasAuthority('mds:stepAttr:view')")
    @GetMapping("/flows/{flowId}/steps/{stepId}/attrs")
    public Result<Map<String, String>> listByStep(
            @PathVariable String flowId, @PathVariable String stepId) {
        return Result.ok(service.listByStep(flowId, stepId));
    }

    @PreAuthorize("hasAuthority('mds:stepAttr:update')")
    @PutMapping("/flows/{flowId}/steps/{stepId}/attrs")
    public Result<Map<String, String>> putForStep(
            @PathVariable String flowId, @PathVariable String stepId,
            @RequestBody ConstraintAttrAdminService.AttrPutReq req) {
        return Result.ok(service.putForStep(flowId, stepId, req));
    }
}
