package com.cim.rms.server.audit;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * 审计日志 API（Req 12/17/18/19 的 RMS 落点、FR-H1~H3）。
 *
 * <p>路径前缀 {@code /api/v1/rms/audit}；认证由 cim-auth-starter 统一（JWT 验签 + rms-ap 准入）。
 * 功能权限码 {@code rms:audit:view} 用于菜单可见性与前端 {@code <Perms>} 门禁。</p>
 */
@RestController
@RequestMapping("/api/v1/rms/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:audit:view')")
public class RmsAuditController {

    private final RmsAuditService service;

    /** 审计流水：多维过滤（类型/结果/操作人/时间范围/关键字）+ 截断（默认 200，上限 1000）。 */
    @GetMapping
    public Result<List<RmsAuditService.AuditView>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        return Result.ok(service.list(
                new RmsAuditService.AuditQuery(type, result, actor, from, to, keyword, limit)));
    }

    /** 汇总统计（FR-H2）。 */
    @GetMapping("/summary")
    public Result<RmsAuditService.AuditSummary> summary() {
        return Result.ok(service.summary());
    }

    /** 配方对象历史查询（FR-H3）：按配方编码在时间范围内追溯操作历史。 */
    @GetMapping("/recipe-history")
    public Result<List<RmsAuditService.AuditView>> recipeHistory(
            @RequestParam String code,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "100") int max) {
        return Result.ok(service.recipeHistory(code, from, to, max));
    }
}
