package com.cim.rms.server.signoff;

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
 * 签核流 API（Req.md FR-L2/L4/L5、Req 3/20/41）。
 *
 * <p>路径前缀 {@code /api/v1/rms/signoffs}；认证由 cim-auth-starter 统一。功能权限码
 * {@code rms:signoff:view}（查看）/ {@code rms:signoff:approve}（审批）用于菜单与前端门禁。</p>
 */
@RestController
@RequestMapping("/api/v1/rms/signoffs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:signoff:view')")
public class RecipeSignoffController {

    private final RecipeSignoffService service;

    /** 签核单列表（可按 {@code status} 与 {@code keyword} 过滤）。 */
    @GetMapping
    public Result<List<RecipeSignoffService.SignoffView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        SignoffStatus st = (status == null || status.isBlank()) ? null : SignoffStatus.valueOf(status);
        return Result.ok(service.list(st, keyword));
    }

    /** 待办与时效 KPI 汇总（须在 {@code /{id}} 之前声明，避免被路径变量吞掉）。 */
    @GetMapping("/summary")
    public Result<RecipeSignoffService.SignoffSummary> summary() {
        return Result.ok(service.summary());
    }

    /** 签核单详情（含参数差异附件与事件时间线）。 */
    @GetMapping("/{id}")
    public Result<RecipeSignoffService.SignoffDetail> detail(@PathVariable String id) {
        return Result.ok(service.detail(id));
    }

    /** 发起签核（指定配方版本，携带发起原因与签核路由）。 */
    @PreAuthorize("hasAuthority('rms:signoff:approve')")
    @PostMapping
    public Result<RecipeSignoffService.SignoffDetail> submit(@RequestBody SubmitReq req) {
        SignRoute route = (req.signRoute() == null || req.signRoute().isBlank())
                ? SignRoute.SINGLE : SignRoute.valueOf(req.signRoute());
        return Result.ok(service.submit(req.recipeId(), req.versionId(), req.reason(), route));
    }

    /** 审批通过（版本生效）。 */
    @PreAuthorize("hasAuthority('rms:signoff:approve')")
    @PostMapping("/{id}/approve")
    public Result<RecipeSignoffService.SignoffView> approve(@PathVariable String id,
                                                            @RequestBody(required = false) DecisionReq req) {
        return Result.ok(service.approve(id, req == null ? null : req.comment()));
    }

    /** 审批拒绝（版本退回 DRAFT）。 */
    @PreAuthorize("hasAuthority('rms:signoff:approve')")
    @PostMapping("/{id}/reject")
    public Result<RecipeSignoffService.SignoffView> reject(@PathVariable String id,
                                                           @RequestBody(required = false) DecisionReq req) {
        return Result.ok(service.reject(id, req == null ? null : req.comment()));
    }

    /** 批量审批（Req 3 / FR-L2），逐项结果（部分成功语义）。 */
    @PreAuthorize("hasAuthority('rms:signoff:approve')")
    @PostMapping("/batch-decision")
    public Result<List<RecipeSignoffService.BatchResult>> batchDecision(@RequestBody BatchReq req) {
        return Result.ok(service.batchDecision(req.ids(), req.decision(), req.comment()));
    }

    /** 预设限时使用（Req 20 / FR-L5）。 */
    @PreAuthorize("hasAuthority('rms:signoff:approve')")
    @PostMapping("/{id}/temp-allow")
    public Result<RecipeSignoffService.SignoffView> tempAllow(@PathVariable String id,
                                                              @RequestBody TempAllowReq req) {
        return Result.ok(service.presetTempAllow(id, req.from(), req.to(), req.scope(), req.reason()));
    }

    /** 取消预设限时使用。 */
    @PreAuthorize("hasAuthority('rms:signoff:approve')")
    @PostMapping("/{id}/temp-cancel")
    public Result<RecipeSignoffService.SignoffView> tempCancel(@PathVariable String id,
                                                               @RequestBody(required = false) TempCancelReq req) {
        return Result.ok(service.cancelTempAllow(id, req == null ? null : req.reason()));
    }

    // ---------- 请求体 ----------

    public record SubmitReq(String recipeId, String versionId, String reason, String signRoute) {
    }

    public record DecisionReq(String comment) {
    }

    public record BatchReq(List<String> ids, String decision, String comment) {
    }

    public record TempAllowReq(String from, String to, String scope, String reason) {
    }

    public record TempCancelReq(String reason) {
    }
}
