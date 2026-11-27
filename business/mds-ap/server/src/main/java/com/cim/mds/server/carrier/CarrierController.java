package com.cim.mds.server.carrier;

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
 * 载具（Carrier/Cassette）API——SEMI E87 Carrier 口径的台账主数据（V31）。
 *
 * <p>路径 {@code /api/v1/mds/carriers}；编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=carrier）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class CarrierController {

    private final CarrierAdminService service;

    @PreAuthorize("hasAuthority('" + MdsPermissions.CARRIER_VIEW + "')")
    @GetMapping("/carriers")
    public Result<List<CarrierAdminService.CarrierView>> carriers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(status, equipmentId, keyword));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CARRIER_CREATE + "')")
    @PostMapping("/carriers")
    public Result<CarrierAdminService.CarrierView> create(
            @RequestBody CarrierAdminService.CarrierReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CARRIER_UPDATE + "')")
    @PutMapping("/carriers/{id}")
    public Result<CarrierAdminService.CarrierView> update(
            @PathVariable String id, @RequestBody CarrierAdminService.CarrierReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.CARRIER_DELETE + "')")
    @DeleteMapping("/carriers/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
