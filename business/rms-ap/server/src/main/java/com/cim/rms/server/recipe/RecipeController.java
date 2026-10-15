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
import org.springframework.security.access.prepost.PreAuthorize;

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
@PreAuthorize("hasAuthority('rms:recipe:view')")
public class RecipeController {

    private final RecipeService service;
    private final RecipeValidationService validationService;
    private final com.cim.rms.server.integration.ToolRecipeMapService mapService;
    private final RecipePortService portService;
    private final com.cim.rms.server.effect.ChangeEffectService changeEffectService;
    private final com.cim.rms.server.recipe.body.RecipeBodyParseService bodyParseService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 配方视角反查：被装载到哪些机台（配方详情抽屉「装载机台」区块，W7 体验整合）。 */
    @GetMapping("/{id}/tools")
    public Result<List<com.cim.rms.server.integration.ToolRecipeMapService.ToolUsage>> tools(
            @PathVariable String id) {
        return Result.ok(mapService.usagesOf(id));
    }

    /**
     * 配方导出（W8 生产化硬门槛 #1：文件级备份/灾备迁移）。
     * {@code ids} 缺省 = 全部未删配方。导出文件含明文 Body（敏感 IP），要求
     * {@code rms:body:export} 权限并强留痕（BodyController 同语义）。
     * 返回 JSON 附件（不经 Result 包装，供浏览器直接下载）。
     */
    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(required = false) List<String> ids) {
        if (!com.cim.rms.server.security.AuthorityUtil.has(com.cim.rms.server.security.RmsPermissions.BODY_EXPORT)) {
            throw new com.cim.spring.support.web.BizException(
                    com.cim.spring.support.web.BizCode.ACCESS_DENIED, "无权导出配方（需 rms:body:export）");
        }
        RecipePortService.ExportFile file = portService.export(ids);
        try {
            byte[] json = objectMapper.writeValueAsBytes(file);
            String fname = "rms-recipes-"
                    + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                            .format(java.time.OffsetDateTime.now()) + ".json";
            return org.springframework.http.ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fname + "\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("导出序列化失败", e);
        }
    }

    /**
     * 配方导入（W8）。{@code conflictStrategy}：SKIP（缺省，同 code 跳过）/
     * NEW_VERSION（同 code 追加新 DRAFT，不自动激活）。逐条独立事务，部分成功。
     */
    @PreAuthorize("hasAuthority('rms:recipe:create')")
    @PostMapping("/import")
    public Result<RecipePortService.ImportReport> importFile(@RequestBody ImportReq req) {
        return Result.ok(portService.importFile(req.content(), req.conflictStrategy()));
    }

    public record ImportReq(String content, String conflictStrategy) {
    }

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

    @PreAuthorize("hasAuthority('rms:recipe:create')")
    @PostMapping
    public Result<RecipeService.RecipeSummary> create(@RequestBody CreateReq req) {
        return Result.ok(service.create(req.code(), req.name(), req.deviceTypeId(), req.areaId(), req.parentId(),
                Boolean.TRUE.equals(req.golden()), req.description(),
                req.bodyFormat(), req.bodyBase64(), req.expectedBodyHash()));
    }

    @PreAuthorize("hasAuthority('rms:recipe:update')")
    @PutMapping("/{id}")
    public Result<RecipeService.RecipeSummary> update(@PathVariable String id, @RequestBody UpdateReq req) {
        return Result.ok(service.update(id, req.name(), req.deviceTypeId(), req.areaId(), req.parentId(),
                req.golden(), req.description()));
    }

    /** 子配方列表（Req 15）。 */
    @GetMapping("/{id}/children")
    public Result<List<RecipeService.RecipeSummary>> children(@PathVariable String id) {
        return Result.ok(service.children(id));
    }

    @PreAuthorize("hasAuthority('rms:recipe:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    /** 另存为（FR-F4，Req 6）。 */
    @PreAuthorize("hasAuthority('rms:recipe:create')")
    @PostMapping("/{id}/copy")
    public Result<RecipeService.RecipeSummary> copy(@PathVariable String id, @RequestBody CopyReq req) {
        return Result.ok(service.copyAs(id, req.newCode(), req.newName()));
    }

    /** 新版本（Body 变更即新版本，Req 42）。 */
    @PreAuthorize("hasAuthority('rms:recipe:version')")
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
    @PreAuthorize("hasAuthority('rms:recipe:activate')")
    @PostMapping("/{id}/versions/{versionId}/activate")
    public Result<RecipeVersion> activate(@PathVariable String id, @PathVariable String versionId) {
        return Result.ok(service.activate(id, versionId));
    }

    /** 版本 Body 的结构化解析（W7：配方详情页的参数/步序数据源）。 */
    @GetMapping("/{id}/versions/{versionId}/parsed")
    public Result<RecipeService.ParsedView> parsed(@PathVariable String id, @PathVariable String versionId) {
        return Result.ok(service.parsed(id, versionId));
    }

    /** 变更效果历史（W11：配方迭代的效果跟踪挂钩）。 */
    @GetMapping("/{id}/change-effects")
    public Result<List<com.cim.rms.server.effect.ChangeEffectService.EffectView>> changeEffects(
            @PathVariable String id) {
        return Result.ok(changeEffectService.list(id));
    }

    /**
     * 已注册的 Body 解析格式（W11 解析器 SPI 开放：第三方格式实现
     * {@code RecipeBodyParser} Bean 即插即用，无需改调用方）。
     */
    @GetMapping("/body-formats")
    public Result<List<com.cim.rms.server.recipe.body.RecipeBodyParseService.FormatInfo>> bodyFormats() {
        return Result.ok(bodyParseService.formats());
    }

    /** 保存前预检（W10 新建向导：解析 + 模板强制键，不落库）。 */
    @PreAuthorize("hasAuthority('rms:recipe:view')")
    @PostMapping("/preview-validate")
    public Result<RecipeValidationService.PreviewResult> previewValidate(@RequestBody PreviewReq req) {
        return Result.ok(validationService.previewParse(req.deviceTypeId(), req.bodyFormat() == null
                        ? null : com.cim.rms.server.recipe.BodyFormat.valueOf(req.bodyFormat()),
                req.bodyBase64()));
    }

    /** 良构 / 可执行性校验（对齐 SEMI E139 的 executable & well-formed 服务）。 */
    @GetMapping("/{id}/versions/{versionId}/validate")
    public Result<RecipeValidationService.ValidationResult> validate(@PathVariable String id,
                                                                    @PathVariable String versionId) {
        return Result.ok(validationService.validate(id, versionId));
    }

    /** 编辑版本 Body：仅 DRAFT 可改（ACTIVE/OBSOLETE 须另存新版本）。 */
    @PreAuthorize("hasAuthority('rms:recipe:version')")
    @PutMapping("/{id}/versions/{versionId}/body")
    public Result<RecipeVersion> updateBody(@PathVariable String id, @PathVariable String versionId,
                                           @RequestBody UpdateBodyReq req) {
        return Result.ok(service.updateVersionBody(id, versionId, req.bodyFormat(), req.bodyBase64(),
                req.expectedBodyHash(), req.changeSummary()));
    }

    // ---------- 请求体 ----------

    public record CreateReq(String code, String name, String deviceTypeId, String areaId, String parentId,
                            Boolean golden, String description,
                            String bodyFormat, String bodyBase64, String expectedBodyHash) {
    }

    public record UpdateReq(String name, String deviceTypeId, String areaId, String parentId,
                            Boolean golden, String description) {
    }

    public record CopyReq(String newCode, String newName) {
    }

    public record NewVersionReq(BodyFormat bodyFormat, String bodyBase64, String expectedBodyHash,
                                String paramSnapshot, String changeSummary) {
    }

    public record UpdateBodyReq(BodyFormat bodyFormat, String bodyBase64, String expectedBodyHash,
                                String changeSummary) {
    }

    public record PreviewReq(String deviceTypeId, String bodyFormat, String bodyBase64) {
    }
}
