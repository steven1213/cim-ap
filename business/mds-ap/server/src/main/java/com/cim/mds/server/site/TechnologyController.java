package com.cim.mds.server.site;

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
 * 制程技术（Technology）API——产品管理域。
 *
 * <p>路径 {@code /api/v1/mds/technologies}；编辑锁复用 {@code /locations/{type}/{id}/lock}
 * （type=technology，见 {@link LocationLockController}）。对外契约统一走 {@code *View}。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class TechnologyController {

    private final TechnologyAdminService service;

    @PreAuthorize("hasAuthority('mds:tech:view')")
    @GetMapping("/technologies")
    public Result<List<TechnologyAdminService.TechnologyView>> technologies(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @PreAuthorize("hasAuthority('mds:tech:create')")
    @PostMapping("/technologies")
    public Result<TechnologyAdminService.TechnologyView> createTechnology(
            @RequestBody TechnologyAdminService.TechnologyReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:tech:update')")
    @PutMapping("/technologies/{id}")
    public Result<TechnologyAdminService.TechnologyView> updateTechnology(
            @PathVariable String id, @RequestBody TechnologyAdminService.TechnologyReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:tech:delete')")
    @DeleteMapping("/technologies/{id}")
    public Result<Void> deleteTechnology(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
