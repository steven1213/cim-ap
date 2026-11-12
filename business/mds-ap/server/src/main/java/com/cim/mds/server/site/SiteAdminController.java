package com.cim.mds.server.site;

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

import java.util.List;

/**
 * 场地主数据 API：Site / Fab / Area 三层。
 *
 * <p>路径前缀 {@code /api/v1/mds}；认证由 cim-auth-starter 统一（JWT 验签 + 准入 403 由
 * {@code MdsSecurityConfig} 收紧为 authenticated），功能权限用方法级
 * {@code @PreAuthorize}（权限码 {@code mds:*:*}，由 {@code MdsConsoleSeedService} 入库）。
 * 对外契约统一走 {@code *View}，不暴露 JPA 实体。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class SiteAdminController {

    private final SiteAdminService service;

    // ---------- 基地 ----------

    @PreAuthorize("hasAuthority('mds:site:view')")
    @GetMapping("/sites")
    public Result<List<SiteAdminService.SiteView>> sites(@RequestParam(required = false) String keyword) {
        return Result.ok(service.listSites(keyword));
    }

    @PreAuthorize("hasAuthority('mds:site:create')")
    @PostMapping("/sites")
    public Result<SiteAdminService.SiteView> createSite(@RequestBody SiteAdminService.SiteReq req) {
        return Result.ok(service.createSite(req));
    }

    @PreAuthorize("hasAuthority('mds:site:update')")
    @PutMapping("/sites/{id}")
    public Result<SiteAdminService.SiteView> updateSite(@PathVariable String id,
                                                       @RequestBody SiteAdminService.SiteReq req) {
        return Result.ok(service.updateSite(id, req));
    }

    @PreAuthorize("hasAuthority('mds:site:delete')")
    @DeleteMapping("/sites/{id}")
    public Result<Void> deleteSite(@PathVariable String id) {
        service.deleteSite(id);
        return Result.ok(null);
    }

    // ---------- Fab ----------

    @PreAuthorize("hasAuthority('mds:fab:view')")
    @GetMapping("/fabs")
    public Result<List<SiteAdminService.FabView>> fabs(@RequestParam(required = false) String siteCode,
                                                       @RequestParam(required = false) String keyword) {
        return Result.ok(service.listFabs(siteCode, keyword));
    }

    @PreAuthorize("hasAuthority('mds:fab:create')")
    @PostMapping("/fabs")
    public Result<SiteAdminService.FabView> createFab(@RequestBody SiteAdminService.FabReq req) {
        return Result.ok(service.createFab(req));
    }

    @PreAuthorize("hasAuthority('mds:fab:update')")
    @PutMapping("/fabs/{id}")
    public Result<SiteAdminService.FabView> updateFab(@PathVariable String id,
                                                      @RequestBody SiteAdminService.FabReq req) {
        return Result.ok(service.updateFab(id, req));
    }

    @PreAuthorize("hasAuthority('mds:fab:delete')")
    @DeleteMapping("/fabs/{id}")
    public Result<Void> deleteFab(@PathVariable String id) {
        service.deleteFab(id);
        return Result.ok(null);
    }

    // ---------- Area ----------

    @PreAuthorize("hasAuthority('mds:area:view')")
    @GetMapping("/areas")
    public Result<List<SiteAdminService.AreaView>> areas(@RequestParam(required = false) String fabCode,
                                                         @RequestParam(required = false) String keyword) {
        return Result.ok(service.listAreas(fabCode, keyword));
    }

    @PreAuthorize("hasAuthority('mds:area:create')")
    @PostMapping("/areas")
    public Result<SiteAdminService.AreaView> createArea(@RequestBody SiteAdminService.AreaReq req) {
        return Result.ok(service.createArea(req));
    }

    @PreAuthorize("hasAuthority('mds:area:update')")
    @PutMapping("/areas/{id}")
    public Result<SiteAdminService.AreaView> updateArea(@PathVariable String id,
                                                        @RequestBody SiteAdminService.AreaReq req) {
        return Result.ok(service.updateArea(id, req));
    }

    @PreAuthorize("hasAuthority('mds:area:delete')")
    @DeleteMapping("/areas/{id}")
    public Result<Void> deleteArea(@PathVariable String id) {
        service.deleteArea(id);
        return Result.ok(null);
    }
}
