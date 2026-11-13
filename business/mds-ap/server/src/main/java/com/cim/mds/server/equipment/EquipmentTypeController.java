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
 * 设备类型（EquipmentType）API。
 *
 * <p>路径 {@code /api/v1/mds/equipment-types}；编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=equipmentType）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class EquipmentTypeController {

    private final EquipmentTypeAdminService service;

    @PreAuthorize("hasAuthority('mds:equipmentType:view')")
    @GetMapping("/equipment-types")
    public Result<List<EquipmentTypeAdminService.EquipmentTypeView>> types(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @PreAuthorize("hasAuthority('mds:equipmentType:create')")
    @PostMapping("/equipment-types")
    public Result<EquipmentTypeAdminService.EquipmentTypeView> create(
            @RequestBody EquipmentTypeAdminService.EquipmentTypeReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:equipmentType:update')")
    @PutMapping("/equipment-types/{id}")
    public Result<EquipmentTypeAdminService.EquipmentTypeView> update(
            @PathVariable String id, @RequestBody EquipmentTypeAdminService.EquipmentTypeReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:equipmentType:delete')")
    @DeleteMapping("/equipment-types/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
