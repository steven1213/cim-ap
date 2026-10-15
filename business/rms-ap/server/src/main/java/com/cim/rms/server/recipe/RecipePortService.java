package com.cim.rms.server.recipe;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.device.DeviceAreaRepository;
import com.cim.rms.server.device.DeviceTypeRepository;
import com.cim.rms.server.spec.ParamSpecService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 配方导入/导出（W8 生产化硬门槛 #1：文件级备份、灾备迁移、跨厂复制）。
 *
 * <p><b>导出</b>：主档 + 全部版本（Body 解密后明文）+ 参数规格 → JSON 文件。
 * 导出文件含完整工艺参数（IP），属敏感资产——要求 {@code rms:body:export} 权限并强留痕。</p>
 *
 * <p><b>导入</b>：逐条独立事务（部分成功语义，单条失败不回滚整体）；
 * 按 code 冲突检测，策略 {@code SKIP}（跳过）/ {@code NEW_VERSION}（追加为最新版本的
 * 下一个 DRAFT，<b>不自动激活</b>——备份恢复后必须走签核链，与业界惯例一致）。
 * Body 导入后走标准写点：自动加密（D9）+ 自动解析（W7）+ hash 对明文计算。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipePortService {

    private static final String SCHEMA = "rms-recipe-export/1";

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final RecipeService recipeService;
    private final BodyStore bodyStore;
    private final ParamSpecService specService;
    private final DeviceTypeRepository deviceTypeRepository;
    private final DeviceAreaRepository deviceAreaRepository;
    private final RmsAuditService audit;

    // ==================== 导出 ====================

    /** 导出（ids 为空 = 全部未删配方）。Body 为解密后明文 Base64。 */
    @Transactional(readOnly = true)
    public ExportFile export(List<String> ids) {
        List<Recipe> recipes = (ids == null || ids.isEmpty())
                ? recipeRepository.findAll().stream().filter(r -> !Boolean.TRUE.equals(r.getDeleted())).toList()
                : ids.stream().map(this::require).toList();
        List<ExportedRecipe> out = new ArrayList<>(recipes.size());
        for (Recipe r : recipes) {
            List<ExportedVersion> versions = versionRepository
                    .findByRecipeIdAndDeletedFalseOrderByVersionNoDesc(r.getId()).stream()
                    .map(v -> new ExportedVersion(v.getVersionNo(), v.getStatus() == null ? null : v.getStatus().name(),
                            v.getBodyFormat() == null ? null : v.getBodyFormat().name(),
                            bodyStore.readPlain(v), v.getBodyHash(), v.getParamSnapshot(), v.getChangeSummary()))
                    .toList();
            List<ExportedSpec> specs = specService.list(r.getId()).stream()
                    .map(s -> new ExportedSpec(s.paramKey(), s.paramName(), s.specType(), s.dataType(),
                            s.targetValue(), s.rangeMin(), s.rangeMax(), s.listValues(),
                            s.tolerancePct(), s.toleranceAbs(), s.unit(), s.enabled()))
                    .toList();
            out.add(new ExportedRecipe(r.getCode(), r.getName(), r.getDescription(),
                    deviceTypeCode(r.getDeviceTypeId()), r.getDeviceTypeId(),
                    areaCode(r.getAreaId()), r.getAreaId(),
                    r.isGolden(), parentCode(r.getParentId()),
                    versions, specs));
        }
        audit.success("RECIPE_EXPORTED", null, "导出配方 x" + out.size());
        log.info("[recipe-port] 导出配方 x{}", out.size());
        return new ExportFile(SCHEMA, OffsetDateTime.now().toString(), out.size() + " recipes", out);
    }

    // ==================== 导入 ====================

    /**
     * 导入。逐条独立事务；返回报告（created/updated/skipped/errors）。
     * <b>本方法不开事务</b>——内层 create/newVersion 各自 @Transactional，
     * 逐条 try/catch 收集错误（AOP rollback-only 陷阱：外层事务内 catch 内层异常会拖垮整体）。
     */
    public ImportReport importFile(String content, String conflictStrategy) {
        ExportFile file = parse(content);
        String strategy = conflictStrategy == null ? "SKIP" : conflictStrategy.toUpperCase();
        if (!"SKIP".equals(strategy) && !"NEW_VERSION".equals(strategy)) {
            throw new BizException(BizCode.PARAM_INVALID, "conflictStrategy 须为 SKIP 或 NEW_VERSION: " + conflictStrategy);
        }
        int created = 0, updated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        for (ExportedRecipe er : file.recipes()) {
            try {
                if (er.code() == null || er.code().isBlank()) {
                    throw new BizException(BizCode.PARAM_INVALID, "配方 code 缺失");
                }
                String deviceTypeId = resolveTypeId(er);
                String areaId = resolveAreaId(er);
                String parentId = resolveParentId(er.parentCode());
                // 导入内容取「导出文件中的最新版本」（列表按版本号倒序，取首个非空 Body）
                ExportedVersion src = er.versions() == null ? null : er.versions().stream()
                        .filter(v -> v.bodyBase64() != null && !v.bodyBase64().isEmpty())
                        .findFirst().orElse(null);
                if (src == null) {
                    throw new BizException(BizCode.PARAM_INVALID, "无可用版本 Body");
                }
                var existing = recipeRepository.findByCodeAndDeletedFalse(er.code());
                if (existing.isEmpty()) {
                    RecipeService.RecipeSummary s = recipeService.create(er.code(), er.name(), deviceTypeId,
                            areaId, parentId, er.golden(), er.description(),
                            src.bodyFormat(), src.bodyBase64(), null);
                    restoreSpecs(s.id(), er);
                    created++;
                    audit.success("RECIPE_IMPORTED", er.code(), "导入建档 v" + src.versionNo());
                } else if ("NEW_VERSION".equals(strategy)) {
                    RecipeVersion v = recipeService.newVersion(existing.get().getId(),
                            parseFormat(src.bodyFormat()), src.bodyBase64(), null,
                            src.paramSnapshot(), "导入自文件 v" + src.versionNo());
                    restoreSpecs(existing.get().getId(), er);
                    updated++;
                    audit.success("RECIPE_IMPORTED", er.code(), "导入新版本 v" + v.getVersionNo());
                } else {
                    skipped++;
                }
            } catch (BizException | IllegalStateException e) {
                errors.add((er.code() == null ? "(no-code)" : er.code()) + ": " + e.getMessage());
                log.warn("[recipe-port] 导入失败 code={} : {}", er.code(), e.getMessage());
            }
        }
        log.info("[recipe-port] 导入完成 created={} updated={} skipped={} errors={}", created, updated, skipped, errors.size());
        return new ImportReport(created, updated, skipped, errors);
    }

    // ==================== 导出文件模型 ====================

    public record ExportFile(String schema, String exportedAt, String source, List<ExportedRecipe> recipes) {
    }

    public record ExportedRecipe(String code, String name, String description,
                                 String deviceTypeCode, String deviceTypeId,
                                 String areaCode, String areaId, boolean golden, String parentCode,
                                 List<ExportedVersion> versions, List<ExportedSpec> specs) {
    }

    /** {@code bodyBase64} 为明文 Base64；versions 按版本号倒序。 */
    public record ExportedVersion(Integer versionNo, String status, String bodyFormat, String bodyBase64,
                                  String bodyHash, String paramSnapshot, String changeSummary) {
    }

    public record ExportedSpec(String paramKey, String paramName, String specType, String dataType,
                               String targetValue, String rangeMin, String rangeMax, String listValues,
                               Double tolerancePct, Double toleranceAbs, String unit, boolean enabled) {
    }

    public record ImportReport(int created, int updated, int skipped, List<String> errors) {
    }

    // ==================== helpers ====================

    private ExportFile parse(String content) {
        if (content == null || content.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "导入内容为空");
        }
        try {
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                    .readValue(content, ExportFile.class);
        } catch (Exception e) {
            throw new BizException(BizCode.PARAM_INVALID, "导入文件不是合法的 RMS 配方导出 JSON: " + e.getMessage());
        }
    }

    private Recipe require(String id) {
        Recipe r = recipeRepository.findById(id).orElse(null);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + id);
        }
        return r;
    }

    private String resolveTypeId(ExportedRecipe er) {
        if (er.deviceTypeCode() != null && !er.deviceTypeCode().isBlank()) {
            var t = deviceTypeRepository.findByCodeAndDeletedFalse(er.deviceTypeCode());
            if (t.isPresent()) {
                return t.get().getId();
            }
        }
        var byId = er.deviceTypeId() == null ? java.util.Optional.<com.cim.rms.server.device.DeviceType>empty()
                : deviceTypeRepository.findById(er.deviceTypeId()).filter(t -> !Boolean.TRUE.equals(t.getDeleted()));
        if (byId.isPresent()) {
            return byId.get().getId();
        }
        throw new BizException(BizCode.PARAM_INVALID,
                "机型不存在（code=" + er.deviceTypeCode() + " id=" + er.deviceTypeId() + "）——先在基础数据建机型");
    }

    private String resolveAreaId(ExportedRecipe er) {
        if (er.areaCode() != null && !er.areaCode().isBlank()) {
            var a = deviceAreaRepository.findByCodeAndDeletedFalse(er.areaCode());
            if (a.isPresent()) {
                return a.get().getId();
            }
        }
        if (er.areaId() == null || er.areaId().isBlank()) {
            return null;
        }
        return deviceAreaRepository.findById(er.areaId())
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .map(a -> a.getId()).orElse(null);
    }

    private String resolveParentId(String parentCode) {
        if (parentCode == null || parentCode.isBlank()) {
            return null;
        }
        return recipeRepository.findByCodeAndDeletedFalse(parentCode)
                .map(Recipe::getId).orElse(null);
    }

    private BodyFormat parseFormat(String name) {
        try {
            return name == null ? null : BodyFormat.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new BizException(BizCode.PARAM_INVALID, "未知 BodyFormat: " + name);
        }
    }

    /** 恢复参数规格（upsert 语义：同 key 覆盖）。 */
    private void restoreSpecs(String recipeId, ExportedRecipe er) {
        if (er.specs() == null) {
            return;
        }
        for (ExportedSpec s : er.specs()) {
            specService.upsert(recipeId, new ParamSpecService.SpecInput(
                    s.paramKey(), s.paramName(), s.specType(), s.dataType(),
                    s.targetValue(), s.rangeMin(), s.rangeMax(), s.listValues(),
                    s.tolerancePct(), s.toleranceAbs(), s.unit(), s.enabled()));
        }
    }

    private String deviceTypeCode(String deviceTypeId) {
        return deviceTypeId == null ? null : deviceTypeRepository.findById(deviceTypeId)
                .map(t -> t.getCode()).orElse(null);
    }

    private String areaCode(String areaId) {
        return areaId == null ? null : deviceAreaRepository.findById(areaId)
                .map(a -> a.getCode()).orElse(null);
    }

    private String parentCode(String parentId) {
        return parentId == null ? null : recipeRepository.findById(parentId)
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .map(Recipe::getCode).orElse(null);
    }
}
