package com.cim.mds.server.site;

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

import java.util.List;

/**
 * 湾 / 设备位主数据 API（Bay / Stage，层级第四、五层），口径同 {@link SiteAdminController}。
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class BayStageAdminController {

    private final BayStageAdminService service;

    // ---------- Bay ----------

    @PreAuthorize("hasAuthority('mds:bay:view')")
    @GetMapping("/bays")
    public Result<List<BayStageAdminService.BayView>> bays(@RequestParam(required = false) String fabCode,
                                                           @RequestParam(required = false) String areaCode,
                                                           @RequestParam(required = false) String keyword) {
        return Result.ok(service.listBays(fabCode, areaCode, keyword));
    }

    @PreAuthorize("hasAuthority('mds:bay:create')")
    @PostMapping("/bays")
    public Result<BayStageAdminService.BayView> createBay(@RequestBody BayStageAdminService.BayReq req) {
        return Result.ok(service.createBay(req));
    }

    @PreAuthorize("hasAuthority('mds:bay:update')")
    @PutMapping("/bays/{id}")
    public Result<BayStageAdminService.BayView> updateBay(@PathVariable String id,
                                                          @RequestBody BayStageAdminService.BayReq req) {
        return Result.ok(service.updateBay(id, req));
    }

    @PreAuthorize("hasAuthority('mds:bay:delete')")
    @DeleteMapping("/bays/{id}")
    public Result<Void> deleteBay(@PathVariable String id) {
        service.deleteBay(id);
        return Result.ok(null);
    }

    // ---------- Stage ----------

    @PreAuthorize("hasAuthority('mds:stage:view')")
    @GetMapping("/stages")
    public Result<List<BayStageAdminService.StageView>> stages(@RequestParam(required = false) String fabCode,
                                                               @RequestParam(required = false) String bayCode,
                                                               @RequestParam(required = false) String keyword) {
        return Result.ok(service.listStages(fabCode, bayCode, keyword));
    }

    @PreAuthorize("hasAuthority('mds:stage:create')")
    @PostMapping("/stages")
    public Result<BayStageAdminService.StageView> createStage(@RequestBody BayStageAdminService.StageReq req) {
        return Result.ok(service.createStage(req));
    }

    @PreAuthorize("hasAuthority('mds:stage:update')")
    @PutMapping("/stages/{id}")
    public Result<BayStageAdminService.StageView> updateStage(@PathVariable String id,
                                                              @RequestBody BayStageAdminService.StageReq req) {
        return Result.ok(service.updateStage(id, req));
    }

    @PreAuthorize("hasAuthority('mds:stage:delete')")
    @DeleteMapping("/stages/{id}")
    public Result<Void> deleteStage(@PathVariable String id) {
        service.deleteStage(id);
        return Result.ok(null);
    }
}
