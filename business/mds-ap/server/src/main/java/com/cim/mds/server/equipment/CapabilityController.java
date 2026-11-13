package com.cim.mds.server.equipment;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备能力（Capability）API——能力字典。
 *
 * <p>路径 {@code /api/v1/mds/capabilities}；编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=capability）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class CapabilityController {

    private final CapabilityAdminService service;

    @PreAuthorize("hasAuthority('mds:capability:view')")
    @GetMapping("/capabilities")
    public Result<List<CapabilityAdminService.CapabilityView>> capabilities(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @PreAuthorize("hasAuthority('mds:capability:create')")
    @PostMapping("/capabilities")
    public Result<CapabilityAdminService.CapabilityView> create(
            @RequestBody CapabilityAdminService.CapabilityReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:capability:update')")
    @PutMapping("/capabilities/{id}")
    public Result<CapabilityAdminService.CapabilityView> update(
            @PathVariable String id, @RequestBody CapabilityAdminService.CapabilityReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:capability:delete')")
    @DeleteMapping("/capabilities/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
