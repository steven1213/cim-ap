package com.cim.mds.server.inspection;

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
 * 检验标准 API——「检什么/怎么检/判多严」的静态契约（V33）。
 *
 * <p>路径 {@code /api/v1/mds/inspection-stds}；明细全量替换在
 * {@code PUT /inspection-stds/{id}/items}；编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=inspectionStd）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionAdminService service;

    @PreAuthorize("hasAuthority('" + MdsPermissions.INSP_VIEW + "')")
    @GetMapping("/inspection-stds")
    public Result<List<InspectionAdminService.StdView>> stds(
            @RequestParam(required = false) String productGroup,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(productGroup, keyword));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.INSP_VIEW + "')")
    @GetMapping("/inspection-stds/{id}/items")
    public Result<List<InspectionAdminService.ItemView>> items(@PathVariable String id) {
        return Result.ok(service.items(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.INSP_CREATE + "')")
    @PostMapping("/inspection-stds")
    public Result<InspectionAdminService.StdView> create(
            @RequestBody InspectionAdminService.StdReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.INSP_UPDATE + "')")
    @PutMapping("/inspection-stds/{id}")
    public Result<InspectionAdminService.StdView> update(
            @PathVariable String id, @RequestBody InspectionAdminService.StdReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.INSP_UPDATE + "')")
    @PutMapping("/inspection-stds/{id}/items")
    public Result<List<InspectionAdminService.ItemView>> replaceItems(
            @PathVariable String id, @RequestBody List<InspectionAdminService.ItemReq> items) {
        return Result.ok(service.replaceItems(id, items));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.INSP_DELETE + "')")
    @DeleteMapping("/inspection-stds/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
