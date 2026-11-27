package com.cim.mds.server.reticle;

import java.util.List;

import com.cim.mds.server.security.MdsPermissions;
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
 * 光罩（Reticle）API——光刻关键耗材级主数据（V32）。
 *
 * <p>路径 {@code /api/v1/mds/reticles}；编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=reticle）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ReticleController {

    private final ReticleAdminService service;

    @PreAuthorize("hasAuthority('" + MdsPermissions.RETICLE_VIEW + "')")
    @GetMapping("/reticles")
    public Result<List<ReticleAdminService.ReticleView>> reticles(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(status, equipmentId, keyword));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.RETICLE_CREATE + "')")
    @PostMapping("/reticles")
    public Result<ReticleAdminService.ReticleView> create(
            @RequestBody ReticleAdminService.ReticleReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.RETICLE_UPDATE + "')")
    @PutMapping("/reticles/{id}")
    public Result<ReticleAdminService.ReticleView> update(
            @PathVariable String id, @RequestBody ReticleAdminService.ReticleReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.RETICLE_DELETE + "')")
    @DeleteMapping("/reticles/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
