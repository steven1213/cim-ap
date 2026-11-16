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
 * Bank 间转运规则 API——「允许从哪个 Bank 搬到哪个 Bank」的 Host 侧路由表
 * （SEMI E88 6.1.1/6.1.2 口径）。
 *
 * <p>路径 {@code /api/v1/mds/bank-transfer-rules}；轻量表不走编辑锁。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class BankTransferRuleController {

    private final BankTransferRuleAdminService service;

    /** bankId 可空：传了查该 Bank 相关规则（作为 from 或 to），不传查全量。 */
    @PreAuthorize("hasAuthority('mds:bank:view')")
    @GetMapping("/bank-transfer-rules")
    public Result<List<BankTransferRuleAdminService.BankTransferRuleView>> rules(
            @RequestParam(required = false) String bankId) {
        return Result.ok(service.list(bankId));
    }

    @PreAuthorize("hasAuthority('mds:bank:create')")
    @PostMapping("/bank-transfer-rules")
    public Result<BankTransferRuleAdminService.BankTransferRuleView> create(
            @RequestBody BankTransferRuleAdminService.BankTransferRuleReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:bank:update')")
    @PutMapping("/bank-transfer-rules/{id}")
    public Result<BankTransferRuleAdminService.BankTransferRuleView> update(
            @PathVariable String id, @RequestBody BankTransferRuleAdminService.BankTransferRuleReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:bank:delete')")
    @DeleteMapping("/bank-transfer-rules/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
