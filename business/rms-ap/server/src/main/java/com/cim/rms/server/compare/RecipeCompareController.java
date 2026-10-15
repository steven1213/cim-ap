package com.cim.rms.server.compare;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * 比对中心 API（Req.md FR-C1~C8、Req 21/24~28/33/37~39）。
 *
 * <p>路径前缀 {@code /api/v1/rms/compares}；认证由 cim-auth-starter 统一。功能权限码
 * {@code rms:compare:view} 用于菜单与前端门禁。</p>
 */
@RestController
@RequestMapping("/api/v1/rms/compares")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:compare:view')")
public class RecipeCompareController {

    private final RecipeCompareService service;

    /** 版本 ↔ 版本比对；不传 {@code mode} 时返回全量差异，传则按方式过滤（Req 27/37）。 */
    @GetMapping("/versions")
    public Result<RecipeCompareService.CompareResult> compareVersions(
            @RequestParam String leftVersionId,
            @RequestParam String rightVersionId,
            @RequestParam(required = false) String mode) {
        if (mode == null || mode.isBlank()) {
            return Result.ok(service.compare(leftVersionId, rightVersionId));
        }
        return Result.ok(service.compare(leftVersionId, rightVersionId, parseMode(mode)));
    }

    /** 运行一次比对并落校验日志。 */
    @PreAuthorize("hasAuthority('rms:compare:view')")
    @PostMapping("/run")
    public Result<RecipeCompareService.CompareRun> run(@RequestBody RunReq req) {
        return Result.ok(service.run(req.baselineVersionId(), req.targetVersionId(), parseMode(req.mode())));
    }

    /** 批量比对：基线版本 vs 多个目标版本（Req 26）。 */
    @PreAuthorize("hasAuthority('rms:compare:view')")
    @PostMapping("/batch")
    public Result<List<RecipeCompareService.CompareRun>> batch(@RequestBody BatchCompareReq req) {
        return Result.ok(service.batch(req.baselineVersionId(), req.targetVersionIds(), parseMode(req.mode())));
    }

    /** 校验日志汇总统计（须在 {@code /logs/{id}} 之前，Req 18）。 */
    @GetMapping("/logs/summary")
    public Result<RecipeCompareService.LogSummary> logSummary() {
        return Result.ok(service.logSummary());
    }

    /** 校验日志列表（Req 17）。 */
    @GetMapping("/logs")
    public Result<List<RecipeCompareService.LogView>> logs(
            @RequestParam(required = false) String result,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        return Result.ok(service.logs(result, limit));
    }

    /** 校验日志明细（差异项逐条，两级下钻）。 */
    @GetMapping("/logs/{id}")
    public Result<RecipeCompareService.LogDetail> logDetail(@PathVariable String id) {
        return Result.ok(service.logDetail(id));
    }

    private static CompareMode parseMode(String mode) {
        return (mode == null || mode.isBlank()) ? CompareMode.FULL_BODY : CompareMode.valueOf(mode);
    }

    // ---------- 请求体 ----------

    public record RunReq(String baselineVersionId, String targetVersionId, String mode) {
    }

    public record BatchCompareReq(String baselineVersionId, List<String> targetVersionIds, String mode) {
    }
}
