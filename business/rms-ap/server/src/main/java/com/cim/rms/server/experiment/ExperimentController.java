package com.cim.rms.server.experiment;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实验配方通道 API（W11）。路径 {@code /api/v1/rms/experiments}。
 * 提交走 {@code rms:experiment:manage}，审批/关闭同权限（实验审批人=工艺主管角色，与签核解耦）。
 */
@RestController
@RequestMapping("/api/v1/rms/experiments")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService service;

    @PreAuthorize("hasAuthority('rms:experiment:view')")
    @GetMapping
    public Result<List<ExperimentService.ExperimentView>> list(
            @RequestParam(required = false) String status) {
        return Result.ok(service.list(status));
    }

    @PreAuthorize("hasAuthority('rms:experiment:manage')")
    @PostMapping
    public Result<ExperimentService.ExperimentView> create(@RequestBody CreateReq req) {
        return Result.ok(service.create(req.recipeId(), req.versionId(), req.deviceId(),
                req.reason(), req.validFrom(), req.validUntil()));
    }

    @PreAuthorize("hasAuthority('rms:experiment:manage')")
    @PostMapping("/{id}/approve")
    public Result<ExperimentService.ExperimentView> approve(@PathVariable String id,
                                                            @RequestBody(required = false) DecisionReq req) {
        return Result.ok(service.approve(id, req == null ? null : req.comment()));
    }

    @PreAuthorize("hasAuthority('rms:experiment:manage')")
    @PostMapping("/{id}/reject")
    public Result<ExperimentService.ExperimentView> reject(@PathVariable String id,
                                                           @RequestBody(required = false) DecisionReq req) {
        return Result.ok(service.reject(id, req == null ? null : req.comment()));
    }

    @PreAuthorize("hasAuthority('rms:experiment:manage')")
    @PostMapping("/{id}/close")
    public Result<ExperimentService.ExperimentView> close(@PathVariable String id,
                                                          @RequestBody(required = false) CloseReq req) {
        return Result.ok(service.close(id, req == null ? null : req.reason()));
    }

    public record CreateReq(String recipeId, String versionId, String deviceId, String reason,
                            String validFrom, String validUntil) {
    }

    public record DecisionReq(String comment) {
    }

    public record CloseReq(String reason) {
    }
}
