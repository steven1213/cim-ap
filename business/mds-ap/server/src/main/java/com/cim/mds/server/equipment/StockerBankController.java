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
 * Stocker 存储区（Bank）API——SEMI E88 zone 口径的主数据。
 *
 * <p>路径 {@code /api/v1/mds/stocker-banks}；编辑锁复用
 * {@code /locations/{type}/{id}/lock}（type=stockerBank）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class StockerBankController {

    private final StockerBankAdminService service;

    @PreAuthorize("hasAuthority('mds:bank:view')")
    @GetMapping("/stocker-banks")
    public Result<List<StockerBankAdminService.StockerBankView>> banks(
            @RequestParam(required = false) String equipmentId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(equipmentId, keyword));
    }

    @PreAuthorize("hasAuthority('mds:bank:create')")
    @PostMapping("/stocker-banks")
    public Result<StockerBankAdminService.StockerBankView> create(
            @RequestBody StockerBankAdminService.StockerBankReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:bank:update')")
    @PutMapping("/stocker-banks/{id}")
    public Result<StockerBankAdminService.StockerBankView> update(
            @PathVariable String id, @RequestBody StockerBankAdminService.StockerBankReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:bank:delete')")
    @DeleteMapping("/stocker-banks/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
