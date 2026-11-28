package com.cim.mds.server.consumable;

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
 * 消耗品 BOM API——「在哪里、消耗多少」的消耗契约（V38）。
 *
 * <p>路径 {@code /api/v1/mds/cons-boms}；明细全量替换在
 * {@code PUT /cons-boms/{id}/lines}；生命周期端点
 * {@code POST /cons-boms/{id}/activate}、{@code POST /cons-boms/{id}/disable}；
 * 编辑锁复用 {@code /locations/{type}/{id}/lock}（type=consBom）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ConsBomController {

    private final ConsBomAdminService service;

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_BOM_VIEW + "')")
    @GetMapping("/cons-boms")
    public Result<List<ConsBomAdminService.BomView>> boms(
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String flowId,
            @RequestParam(required = false) String operationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(productId, flowId, operationId, status, keyword));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_BOM_VIEW + "')")
    @GetMapping("/cons-boms/{id}/lines")
    public Result<List<ConsBomAdminService.LineView>> lines(@PathVariable String id) {
        return Result.ok(service.lines(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_BOM_CREATE + "')")
    @PostMapping("/cons-boms")
    public Result<ConsBomAdminService.BomView> create(
            @RequestBody ConsBomAdminService.BomReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_BOM_UPDATE + "')")
    @PutMapping("/cons-boms/{id}")
    public Result<ConsBomAdminService.BomView> update(
            @PathVariable String id, @RequestBody ConsBomAdminService.BomReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_BOM_UPDATE + "')")
    @PutMapping("/cons-boms/{id}/lines")
    public Result<List<ConsBomAdminService.LineView>> replaceLines(
            @PathVariable String id, @RequestBody List<ConsBomAdminService.LineReq> lines) {
        return Result.ok(service.replaceLines(id, lines));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_BOM_UPDATE + "')")
    @PostMapping("/cons-boms/{id}/activate")
    public Result<ConsBomAdminService.BomView> activate(@PathVariable String id) {
        return Result.ok(service.activate(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_BOM_UPDATE + "')")
    @PostMapping("/cons-boms/{id}/disable")
    public Result<ConsBomAdminService.BomView> disable(@PathVariable String id) {
        return Result.ok(service.disable(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_BOM_DELETE + "')")
    @DeleteMapping("/cons-boms/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
