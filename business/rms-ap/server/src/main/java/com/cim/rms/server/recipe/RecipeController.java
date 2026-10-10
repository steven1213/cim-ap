package com.cim.rms.server.recipe;

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
 * 配方管理 API（Req 1/2/4/5/6/13/14/42 的 W1 切片）。
 *
 * <p>路径前缀 {@code /api/v1/rms/recipes}；认证由 cim-auth-starter 统一
 * （JWT 验签 + rms-ap 准入）；内部功能权限（{@code rms:recipe:*}）W2 接 RBAC 后加方法级校验。</p>
 */
@RestController
@RequestMapping("/api/v1/rms/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService service;

    /** 通配符查询（FR-F2）：{@code *}/{@code ?} 语义。 */
    @GetMapping
    public Result<List<RecipeService.RecipeSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String deviceTypeId,
            @RequestParam(required = false) String areaId,
            @RequestParam(required = false) Boolean golden) {
        return Result.ok(service.list(keyword, deviceTypeId, areaId, golden));
    }

    @GetMapping("/{id}")
    public Result<RecipeService.RecipeDetail> detail(@PathVariable String id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    public Result<RecipeService.RecipeSummary> create(@RequestBody CreateReq req) {
        return Result.ok(service.create(req.code(), req.name(), req.deviceTypeId(), req.areaId(),
                Boolean.TRUE.equals(req.golden()), req.description(),
                req.bodyFormat(), req.bodyBase64(), req.expectedBodyHash()));
    }

    @PutMapping("/{id}")
    public Result<RecipeService.RecipeSummary> update(@PathVariable String id, @RequestBody UpdateReq req) {
        return Result.ok(service.update(id, req.name(), req.deviceTypeId(), req.areaId(),
                req.golden(), req.description()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    /** 另存为（FR-F4，Req 6）。 */
    @PostMapping("/{id}/copy")
    public Result<RecipeService.RecipeSummary> copy(@PathVariable String id, @RequestBody CopyReq req) {
        return Result.ok(service.copyAs(id, req.newCode(), req.newName()));
    }

    /** 新版本（Body 变更即新版本，Req 42）。 */
    @PostMapping("/{id}/versions")
    public Result<RecipeVersion> newVersion(@PathVariable String id, @RequestBody NewVersionReq req) {
        return Result.ok(service.newVersion(id, req.bodyFormat(), req.bodyBase64(),
                req.expectedBodyHash(), req.paramSnapshot(), req.changeSummary()));
    }

    /** 版本列表（版本号倒序）。 */
    @GetMapping("/{id}/versions")
    public Result<List<RecipeVersion>> versions(@PathVariable String id) {
        return Result.ok(service.versionsOf(id));
    }

    /** 激活（W1 简化路径；W2 起前置签核通过，Req 3/41）。 */
    @PostMapping("/{id}/versions/{versionId}/activate")
    public Result<RecipeVersion> activate(@PathVariable String id, @PathVariable String versionId) {
        return Result.ok(service.activate(id, versionId));
    }

    // ---------- 请求体 ----------

    public record CreateReq(String code, String name, String deviceTypeId, String areaId,
                            Boolean golden, String description,
                            String bodyFormat, String bodyBase64, String expectedBodyHash) {
    }

    public record UpdateReq(String name, String deviceTypeId, String areaId,
                            Boolean golden, String description) {
    }

    public record CopyReq(String newCode, String newName) {
    }

    public record NewVersionReq(BodyFormat bodyFormat, String bodyBase64, String expectedBodyHash,
                                String paramSnapshot, String changeSummary) {
    }
}
