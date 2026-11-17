package com.cim.mds.server.reason;

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
 * 原因码（V16）API——主数据 {@code /api/v1/mds/reason-code-types|reason-codes}
 * + WIP 事件示例 {@code /api/v1/mds/wip-reason-events}。
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ReasonCodeController {

    private final ReasonCodeAdminService adminService;
    private final WipReasonEventService eventService;

    // ---------------------------------------------------------------- 类型

    /** 类型清单（含挂靠码计数）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_VIEW + "')")
    @GetMapping("/reason-code-types")
    public Result<List<ReasonCodeAdminService.TypeView>> listTypes() {
        return Result.ok(adminService.listTypes());
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_CREATE + "')")
    @PostMapping("/reason-code-types")
    public Result<ReasonCodeAdminService.TypeView> createType(
            @RequestBody ReasonCodeAdminService.TypeReq req) {
        return Result.ok(adminService.createType(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_UPDATE + "')")
    @PutMapping("/reason-code-types/{id}")
    public Result<ReasonCodeAdminService.TypeView> updateType(
            @PathVariable String id, @RequestBody ReasonCodeAdminService.TypeReq req) {
        return Result.ok(adminService.updateType(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_DELETE + "')")
    @DeleteMapping("/reason-code-types/{id}")
    public Result<Void> deleteType(@PathVariable String id) {
        adminService.deleteType(id);
        return Result.ok(null);
    }

    // ---------------------------------------------------------------- 原因码

    /** 码清单（typeKey 过滤 + keyword 模糊；node_path 升序 = 树展开序）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_VIEW + "')")
    @GetMapping("/reason-codes")
    public Result<List<ReasonCodeAdminService.ReasonView>> listCodes(
            @RequestParam(required = false) String typeKey,
            @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.listCodes(typeKey, keyword));
    }

    /** WIP 下拉口径：type 匹配 + 启用。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_VIEW + "')")
    @GetMapping("/reason-codes/enabled")
    public Result<List<ReasonCodeAdminService.ReasonView>> listEnabled(
            @RequestParam String typeKey) {
        return Result.ok(adminService.listEnabledByType(typeKey));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_CREATE + "')")
    @PostMapping("/reason-codes")
    public Result<ReasonCodeAdminService.ReasonView> createCode(
            @RequestBody ReasonCodeAdminService.ReasonCreateReq req) {
        return Result.ok(adminService.createCode(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_UPDATE + "')")
    @PutMapping("/reason-codes/{id}")
    public Result<ReasonCodeAdminService.ReasonView> updateCode(
            @PathVariable String id, @RequestBody ReasonCodeAdminService.ReasonUpdateReq req) {
        return Result.ok(adminService.updateCode(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_DELETE + "')")
    @DeleteMapping("/reason-codes/{id}")
    public Result<Void> deleteCode(@PathVariable String id) {
        adminService.deleteCode(id);
        return Result.ok(null);
    }

    // ---------------------------------------------------------------- WIP 事件（示例域）

    /** 事件履历（lotId 可选过滤；按发生时间倒序）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_VIEW + "')")
    @GetMapping("/wip-reason-events")
    public Result<List<WipReasonEventService.EventView>> listEvents(
            @RequestParam(required = false) String lotId) {
        return Result.ok(eventService.list(lotId));
    }

    /** 创建事件（快照五件套同事务落库）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.REASON_UPDATE + "')")
    @PostMapping("/wip-reason-events")
    public Result<WipReasonEventService.EventView> createEvent(
            @RequestBody WipReasonEventService.EventCreateReq req) {
        return Result.ok(eventService.create(req));
    }
}
