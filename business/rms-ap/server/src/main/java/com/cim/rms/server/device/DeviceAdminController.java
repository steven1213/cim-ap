package com.cim.rms.server.device;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
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
 * 设备管理 API（Req 46/47/48）。
 *
 * <p>路径前缀 {@code /api/v1/rms/device*}；认证由 cim-auth-starter 统一
 * （JWT 验签 + rms-ap 准入）；内部功能权限（{@code rms:device:*}）W2 接 RBAC 后加方法级校验。</p>
 */
@RestController
@RequestMapping("/api/v1/rms")
@RequiredArgsConstructor
public class DeviceAdminController {

    private final DeviceAdminService service;

    // ---------- 设备类型 ----------

    @GetMapping("/device-types")
    public Result<List<DeviceType>> types() {
        return Result.ok(service.listTypes());
    }

    @PostMapping("/device-types")
    public Result<DeviceType> createType(@RequestBody TypeReq req) {
        return Result.ok(service.createType(req.code(), req.name(), req.description()));
    }

    @PutMapping("/device-types/{id}")
    public Result<DeviceType> updateType(@PathVariable String id, @RequestBody TypeReq req) {
        return Result.ok(service.updateType(id, req.name(), req.description()));
    }

    @DeleteMapping("/device-types/{id}")
    public Result<Void> deleteType(@PathVariable String id) {
        service.deleteType(id);
        return Result.ok(null);
    }

    // ---------- 设备区域 ----------

    @GetMapping("/device-areas")
    public Result<List<DeviceArea>> areas() {
        return Result.ok(service.listAreas());
    }

    @PostMapping("/device-areas")
    public Result<DeviceArea> createArea(@RequestBody AreaReq req) {
        return Result.ok(service.createArea(req.code(), req.name(), req.parentId(), req.sortNo(), req.description()));
    }

    @PutMapping("/device-areas/{id}")
    public Result<DeviceArea> updateArea(@PathVariable String id, @RequestBody AreaReq req) {
        return Result.ok(service.updateArea(id, req.name(), req.sortNo(), req.description()));
    }

    @DeleteMapping("/device-areas/{id}")
    public Result<Void> deleteArea(@PathVariable String id) {
        service.deleteArea(id);
        return Result.ok(null);
    }

    // ---------- 设备台账 ----------

    @GetMapping("/devices")
    public Result<List<Device>> devices(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deviceTypeId,
            @RequestParam(required = false) String areaId,
            @RequestParam(required = false) DeviceStatus status) {
        return Result.ok(service.listDevices(keyword, deviceTypeId, areaId, status));
    }

    @PostMapping("/devices")
    public Result<Device> createDevice(@RequestBody DeviceReq req) {
        return Result.ok(service.createDevice(req.code(), req.name(), req.deviceTypeId(), req.areaId(), req.description()));
    }

    @PutMapping("/devices/{id}")
    public Result<Device> updateDevice(@PathVariable String id, @RequestBody DeviceReq req) {
        return Result.ok(service.updateDevice(id, req.name(), req.areaId(), req.status(), req.description()));
    }

    @DeleteMapping("/devices/{id}")
    public Result<Void> deleteDevice(@PathVariable String id) {
        service.deleteDevice(id);
        return Result.ok(null);
    }

    // ---------- 请求体 ----------

    public record TypeReq(String code, String name, String description) {
    }

    public record AreaReq(String code, String name, String parentId, Integer sortNo, String description) {
    }

    public record DeviceReq(String code, String name, String deviceTypeId, String areaId,
                            DeviceStatus status, String description) {
    }
}
