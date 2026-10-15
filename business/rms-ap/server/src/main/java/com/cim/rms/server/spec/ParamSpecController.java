package com.cim.rms.server.spec;

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
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

/**
 * 参数规格 / 模板 API（FR-S1~S4；Req 29/32/34/35/36）。
 *
 * <p>路径前缀 {@code /api/v1/rms/specs}。</p>
 */
@RestController
@RequestMapping("/api/v1/rms/specs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:spec:view')")
public class ParamSpecController {

    private final ParamSpecService service;

    // ---------- 配方规格 ----------

    @GetMapping
    public Result<List<ParamSpecService.SpecView>> list(@RequestParam String recipeId) {
        return Result.ok(service.list(recipeId));
    }

    @PreAuthorize("hasAuthority('rms:spec:manage')")
    @PostMapping("/recipes/{recipeId}")
    public Result<ParamSpecService.SpecView> upsert(@PathVariable String recipeId,
                                                    @RequestBody ParamSpecService.SpecInput in) {
        return Result.ok(service.upsert(recipeId, in));
    }

    @PreAuthorize("hasAuthority('rms:spec:manage')")
    @PutMapping("/{id}/enabled")
    public Result<ParamSpecService.SpecView> setEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        return Result.ok(service.setEnabled(id, enabled));
    }

    @PreAuthorize("hasAuthority('rms:spec:manage')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    // ---------- 模板 ----------

    @GetMapping("/templates")
    public Result<List<ParamSpecService.TemplateView>> templates() {
        return Result.ok(service.listTemplates());
    }

    @GetMapping("/templates/{id}/items")
    public Result<List<ParamSpecService.TemplateItemView>> templateItems(@PathVariable String id) {
        return Result.ok(service.templateItems(id));
    }

    @PreAuthorize("hasAuthority('rms:spec:manage')")
    @PostMapping("/templates")
    public Result<ParamSpecService.TemplateView> createTemplate(@RequestBody ParamSpecService.TemplateInput in) {
        return Result.ok(service.saveTemplate(null, in));
    }

    @PreAuthorize("hasAuthority('rms:spec:manage')")
    @PutMapping("/templates/{id}")
    public Result<ParamSpecService.TemplateView> updateTemplate(@PathVariable String id,
                                                                @RequestBody ParamSpecService.TemplateInput in) {
        return Result.ok(service.saveTemplate(id, in));
    }

    @PreAuthorize("hasAuthority('rms:spec:manage')")
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable String id) {
        service.deleteTemplate(id);
        return Result.ok(null);
    }

    /** 整体应用模板到配方（Req 35）。 */
    @PreAuthorize("hasAuthority('rms:spec:manage')")
    @PostMapping("/apply-template")
    public Result<Integer> applyTemplate(@RequestBody ApplyReq req) {
        return Result.ok(service.applyTemplate(req.recipeId(), req.templateCode()));
    }

    /** 继承规格（Req 32/FR-S3）。 */
    @PreAuthorize("hasAuthority('rms:spec:manage')")
    @PostMapping("/inherit")
    public Result<Integer> inherit(@RequestBody InheritReq req) {
        return Result.ok(service.copyFrom(req.targetRecipeId(), req.sourceRecipeId()));
    }

    /** 参数快照按规格集判定（返回失败项；FR-C5）。 */
    @PreAuthorize("hasAuthority('rms:spec:manage')")
    @PostMapping("/check")
    public Result<List<ParamSpecService.SpecCheck>> check(@RequestBody CheckReq req) {
        return Result.ok(service.check(req.recipeId(), req.params() == null ? Map.of() : req.params()));
    }

    public record ApplyReq(String recipeId, String templateCode) {
    }

    public record InheritReq(String targetRecipeId, String sourceRecipeId) {
    }

    public record CheckReq(String recipeId, Map<String, String> params) {
    }
}
