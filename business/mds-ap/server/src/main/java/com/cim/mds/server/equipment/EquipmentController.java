package com.cim.mds.server.equipment;

import java.util.List;
import java.util.Set;

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
 * 设备（Equipment）API——含能力关联端点。
 *
 * <p>路径 {@code /api/v1/mds/equipments}；页面视图用 {@code view=process|amhs}
 * 映射为类别过滤（设备页/搬运设备页共用一套 CRUD）。编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=equipment）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentAdminService service;
    private final EquipmentUnitAdminService unitService;

    @PreAuthorize("hasAuthority('mds:equipment:view')")
    @GetMapping("/equipments")
    public Result<List<EquipmentAdminService.EquipmentView>> equipments(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String classCode,
            @RequestParam(required = false) String ownerUser,
            @RequestParam(required = false, defaultValue = "all") String view) {
        boolean processOnly = "process".equals(view);
        boolean amhsOnly = "amhs".equals(view);
        return Result.ok(service.list(keyword, classCode, ownerUser, processOnly, amhsOnly));
    }

    /** 单机台详情（主从页右侧详情块直查）。 */
    @PreAuthorize("hasAuthority('mds:equipment:view')")
    @GetMapping("/equipments/{id}")
    public Result<EquipmentAdminService.EquipmentView> get(@PathVariable String id) {
        return Result.ok(service.get(id));
    }

    /** 能力 → 可加工设备解析（V21，AMAT 口径）：Step.capabilityCode 反查挂该能力的设备清单。 */
    @PreAuthorize("hasAuthority('mds:equipment:view')")
    @GetMapping("/equipments/by-capability")
    public Result<List<EquipmentAdminService.EquipmentView>> byCapability(
            @RequestParam String capabilityCode) {
        return Result.ok(service.byCapability(capabilityCode));
    }

    @PreAuthorize("hasAuthority('mds:equipment:create')")
    @PostMapping("/equipments")
    public Result<EquipmentAdminService.EquipmentView> create(
            @RequestBody EquipmentAdminService.EquipmentReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:equipment:update')")
    @PutMapping("/equipments/{id}")
    public Result<EquipmentAdminService.EquipmentView> update(
            @PathVariable String id, @RequestBody EquipmentAdminService.EquipmentReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:equipment:delete')")
    @DeleteMapping("/equipments/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    @PreAuthorize("hasAuthority('mds:equipment:view')")
    @GetMapping("/equipments/{id}/capabilities")
    public Result<List<EquipmentAdminService.EqpCapItem>> capabilities(@PathVariable String id) {
        return Result.ok(service.capabilities(id));
    }

    @PreAuthorize("hasAuthority('mds:equipment:update')")
    @PutMapping("/equipments/{id}/capabilities")
    public Result<List<EquipmentAdminService.EqpCapItem>> setCapabilities(
            @PathVariable String id,
            @RequestBody List<EquipmentAdminService.EqpCapItem> items) {
        return Result.ok(service.setCapabilities(id, items));
    }

    // ------------------------------------------------------------------ 子单元（E120 结构树）

    @PreAuthorize("hasAuthority('mds:equipment:view')")
    @GetMapping("/equipments/{equipmentId}/units")
    public Result<List<EquipmentUnitAdminService.EquipmentUnitView>> units(
            @PathVariable String equipmentId) {
        return Result.ok(unitService.list(equipmentId));
    }

    @PreAuthorize("hasAuthority('mds:equipment:update')")
    @PostMapping("/equipments/{equipmentId}/units")
    public Result<EquipmentUnitAdminService.EquipmentUnitView> createUnit(
            @PathVariable String equipmentId,
            @RequestBody EquipmentUnitAdminService.EquipmentUnitReq req) {
        return Result.ok(unitService.create(equipmentId, req));
    }

    @PreAuthorize("hasAuthority('mds:equipment:update')")
    @PutMapping("/equipments/{equipmentId}/units/{unitId}")
    public Result<EquipmentUnitAdminService.EquipmentUnitView> updateUnit(
            @PathVariable String equipmentId, @PathVariable String unitId,
            @RequestBody EquipmentUnitAdminService.EquipmentUnitReq req) {
        return Result.ok(unitService.update(equipmentId, unitId, req));
    }

    @PreAuthorize("hasAuthority('mds:equipment:delete')")
    @DeleteMapping("/equipments/{equipmentId}/units/{unitId}")
    public Result<Void> deleteUnit(@PathVariable String equipmentId, @PathVariable String unitId) {
        unitService.delete(equipmentId, unitId);
        return Result.ok(null);
    }
}
