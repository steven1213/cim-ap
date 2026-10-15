package com.cim.rms.server.golden;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * Golden 机差覆盖层 API（FR-G1~G3，Req 16/30/31）。路径前缀 {@code /api/v1/rms/golden-overrides}。
 */
@RestController
@RequestMapping("/api/v1/rms/golden-overrides")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:golden:view')")
public class GoldenController {

    private final GoldenService service;

    @GetMapping
    public Result<List<GoldenService.OverrideView>> list(@RequestParam(required = false) String deviceId,
                                                         @RequestParam(required = false) String status) {
        return Result.ok(service.list(deviceId, status));
    }

    @PreAuthorize("hasAuthority('rms:golden:manage')")
    @PostMapping
    public Result<GoldenService.OverrideView> submit(@RequestBody SubmitReq req) {
        return Result.ok(service.submit(req.deviceId(), req.recipeId(), req.paramKey(),
                req.allowMin(), req.allowMax(), req.reason()));
    }

    @PreAuthorize("hasAuthority('rms:golden:manage')")
    @PostMapping("/{id}/approve")
    public Result<GoldenService.OverrideView> approve(@PathVariable String id, @RequestBody(required = false) DecisionReq req) {
        return Result.ok(service.approve(id, req == null ? null : req.comment()));
    }

    @PreAuthorize("hasAuthority('rms:golden:manage')")
    @PostMapping("/{id}/reject")
    public Result<GoldenService.OverrideView> reject(@PathVariable String id, @RequestBody(required = false) DecisionReq req) {
        return Result.ok(service.reject(id, req == null ? null : req.comment()));
    }

    @PreAuthorize("hasAuthority('rms:golden:manage')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    public record SubmitReq(String deviceId, String recipeId, String paramKey,
                            String allowMin, String allowMax, String reason) {
    }

    public record DecisionReq(String comment) {
    }
}
