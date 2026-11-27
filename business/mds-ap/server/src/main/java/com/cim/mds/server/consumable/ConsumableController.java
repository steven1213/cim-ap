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
 * 耗材（Consumable）API——生产辅助物料台账级主数据（V34）。
 *
 * <p>路径 {@code /api/v1/mds/consumables}；编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=consumable）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ConsumableController {

    private final ConsumableAdminService service;

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_VIEW + "')")
    @GetMapping("/consumables")
    public Result<List<ConsumableAdminService.ConsumableView>> consumables(
            @RequestParam(required = false) String consType,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(consType, equipmentId, keyword));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_CREATE + "')")
    @PostMapping("/consumables")
    public Result<ConsumableAdminService.ConsumableView> create(
            @RequestBody ConsumableAdminService.ConsumableReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_UPDATE + "')")
    @PutMapping("/consumables/{id}")
    public Result<ConsumableAdminService.ConsumableView> update(
            @PathVariable String id, @RequestBody ConsumableAdminService.ConsumableReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CONS_DELETE + "')")
    @DeleteMapping("/consumables/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
