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
 * 机型模板（EquipmentClass）API（v2）。
 *
 * <p>路径 {@code /api/v1/mds/equipment-classes}；编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=equipmentClass）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class EquipmentClassController {

    private final EquipmentClassAdminService service;

    @PreAuthorize("hasAuthority('mds:equipmentClass:view')")
    @GetMapping("/equipment-classes")
    public Result<List<EquipmentClassAdminService.EquipmentClassView>> classes(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @PreAuthorize("hasAuthority('mds:equipmentClass:create')")
    @PostMapping("/equipment-classes")
    public Result<EquipmentClassAdminService.EquipmentClassView> create(
            @RequestBody EquipmentClassAdminService.EquipmentClassReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:equipmentClass:update')")
    @PutMapping("/equipment-classes/{id}")
    public Result<EquipmentClassAdminService.EquipmentClassView> update(
            @PathVariable String id, @RequestBody EquipmentClassAdminService.EquipmentClassReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:equipmentClass:delete')")
    @DeleteMapping("/equipment-classes/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
