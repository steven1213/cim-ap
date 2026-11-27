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
 * 机台组（ToolGroup）API（v2 新增，ISA-95 Work Center）。
 *
 * <p>路径 {@code /api/v1/mds/tool-groups}；编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=toolGroup）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ToolGroupController {

    private final ToolGroupAdminService service;

    @PreAuthorize("hasAuthority('mds:toolGroup:view')")
    @GetMapping("/tool-groups")
    public Result<List<ToolGroupAdminService.ToolGroupView>> groups(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String classCode) {
        return Result.ok(service.list(keyword, classCode));
    }

    @PreAuthorize("hasAuthority('mds:toolGroup:create')")
    @PostMapping("/tool-groups")
    public Result<ToolGroupAdminService.ToolGroupView> create(
            @RequestBody ToolGroupAdminService.ToolGroupReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:toolGroup:update')")
    @PutMapping("/tool-groups/{id}")
    public Result<ToolGroupAdminService.ToolGroupView> update(
            @PathVariable String id, @RequestBody ToolGroupAdminService.ToolGroupReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:toolGroup:delete')")
    @DeleteMapping("/tool-groups/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    @PreAuthorize("hasAuthority('mds:toolGroup:update')")
    @PostMapping("/tool-groups/{id}/members/{equipmentId}")
    public Result<ToolGroupAdminService.ToolGroupView> addMember(
            @PathVariable String id, @PathVariable String equipmentId) {
        return Result.ok(service.addMember(id, equipmentId));
    }

    @PreAuthorize("hasAuthority('mds:toolGroup:update')")
    @DeleteMapping("/tool-groups/{id}/members/{equipmentId}")
    public Result<ToolGroupAdminService.ToolGroupView> removeMember(
            @PathVariable String id, @PathVariable String equipmentId) {
        return Result.ok(service.removeMember(id, equipmentId));
    }
}
