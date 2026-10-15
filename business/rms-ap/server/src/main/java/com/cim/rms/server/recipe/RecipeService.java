package com.cim.rms.server.recipe;

import com.cim.core.port.IdGenerator;
import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.common.Wildcards;
import com.cim.rms.server.device.DeviceAdminService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配方管理（Req 1/2/4/5/6/13/14/42 的 W1 落地）。
 *
 * <p><b>版本规则</b>（Req.md §3.2 状态机 + 已拍板 D2）：任何 Body 变更即<b>新版本</b>
 * （DRAFT）；生效走显式激活（W2 接签核后激活前置为签核通过）；同 Recipe 至多一个 ACTIVE，
 * 激活时旧 ACTIVE 自动转 OBSOLETE；回退 = 以历史版本内容新建版本。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final DeviceAdminService deviceAdminService;
    private final com.cim.rms.server.qualification.QualificationService qualificationService;
    private final com.cim.rms.server.recipe.body.RecipeBodyParseService bodyParseService;
    private final BodyStore bodyStore;
    private final com.cim.rms.server.effect.ChangeEffectService changeEffectService;
    private final IdGenerator idGenerator;
    private final RmsAuditService audit;

    // ---------- 查询（FR-F2） ----------

    /** 通配符查询（FR-F2）：{@code *} 任意串、{@code ?} 单字符，其余转义。 */
    public List<RecipeSummary> list(String keyword, String deviceTypeId, String areaId, Boolean golden) {
        String like = Wildcards.toLikePattern(keyword);
        return recipeRepository.search(like, blankToNull(deviceTypeId), blankToNull(areaId), golden,
                Sort.by(Sort.Order.asc("code"))).stream().map(this::toSummary).toList();
    }

    public Recipe require(String id) {
        Recipe r = recipeRepository.findById(id).orElse(null);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + id);
        }
        return r;
    }

    /** 详情（主档摘要 + 全部版本，版本号倒序）。 */
    public RecipeDetail detail(String id) {
        Recipe r = require(id);
        return new RecipeDetail(toSummary(r), versionsOf(id));
    }

    public List<RecipeVersion> versionsOf(String recipeId) {
        require(recipeId);
        return versionRepository.findByRecipeIdAndDeletedFalseOrderByVersionNoDesc(recipeId);
    }

    /** 版本 Body 的结构化解析视图（W7：配方详情页的数据源）。 */
    public record ParsedView(String versionId, String bodyFormat, boolean ok, String error,
                             String paramSnapshot, com.cim.rms.server.recipe.body.ParsedRecipe parsed) {
    }

    public ParsedView parsed(String recipeId, String versionId) {
        require(recipeId);
        RecipeVersion v = requireVersion(versionId);
        if (!v.getRecipeId().equals(recipeId)) {
            throw new BizException(BizCode.PARAM_INVALID, "版本不属于该配方: " + versionId);
        }
        com.cim.rms.server.recipe.body.RecipeBodyParseService.Outcome o =
                bodyParseService.parseOutcome(v.getBodyFormat(), bodyStore.readPlain(v));
        return new ParsedView(versionId, v.getBodyFormat() == null ? null : v.getBodyFormat().name(),
                o.ok(), o.error(), v.getParamSnapshot(), o.parsed());
    }

    // ---------- 建档（FR-F1，Req 1/2） ----------

    @Transactional
    public RecipeSummary create(String code, String name, String deviceTypeId, String areaId, String parentId,
                               boolean golden, String description,
                               String bodyFormat, String bodyBase64, String expectedBodyHash) {
        if (recipeRepository.findByCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "配方编码已存在: " + code);
        }
        deviceAdminService.requireType(deviceTypeId);
        Recipe r = new Recipe();
        r.setCode(code);
        r.setName(name);
        r.setDeviceTypeId(deviceTypeId);
        r.setAreaId(areaId == null || areaId.isBlank() ? null
                : deviceAdminService.requireArea(areaId).getId());
        // 主/子配方层级（Req 15）：解析父配方并计算层级深度
        Recipe parent = parentId == null || parentId.isBlank() ? null : require(parentId);
        r.setParentId(parent == null ? null : parent.getId());
        r.setRecipeLevel(parent == null ? 0 : parent.getRecipeLevel() + 1);
        r.setGolden(golden);
        r.setDescription(description);
        Recipe saved = recipeRepository.save(r);
        // 初始版本 v1：若建档时即带 Body 则一并写入（Body 变更即新版本，Req 42）
        if (bodyBase64 != null && !bodyBase64.isBlank()) {
            BodyFormat fmt = bodyFormat == null || bodyFormat.isBlank()
                    ? BodyFormat.TEXT : BodyFormat.valueOf(bodyFormat);
            newVersionInternal(saved, fmt, bodyBase64, null, "初始版本", expectedBodyHash);
        } else {
            newVersionInternal(saved, null, null, null, "初始版本", null);
        }
        log.info("[recipe] 新建配方 code={} name={} golden={} parent={}", code, name, golden,
                parent == null ? "-" : parent.getCode());
        audit.success("RECIPE_CREATED", code, name);
        return toSummary(saved);
    }

    /** 主档元数据维护（不触碰版本）。 */
    @Transactional
    public RecipeSummary update(String id, String name, String deviceTypeId, String areaId, String parentId,
                               Boolean golden, String description) {
        Recipe r = require(id);
        if (name != null && !name.isBlank()) {
            r.setName(name);
        }
        if (deviceTypeId != null && !deviceTypeId.isBlank()) {
            r.setDeviceTypeId(deviceAdminService.requireType(deviceTypeId).getId());
        }
        if (areaId != null) {
            r.setAreaId(areaId.isBlank() ? null : deviceAdminService.requireArea(areaId).getId());
        }
        if (parentId != null) {
            // 改父：防自指与成环（Req 15 不限层级，但禁止环）
            if (parentId.isBlank()) {
                r.setParentId(null);
                r.setRecipeLevel(0);
            } else {
                if (parentId.equals(r.getId())) {
                    throw new BizException(BizCode.PARAM_INVALID, "配方父级不能是自身: " + r.getCode());
                }
                Recipe parent = require(parentId);
                assertNoCycle(r.getId(), parent);
                r.setParentId(parent.getId());
                r.setRecipeLevel(parent.getRecipeLevel() + 1);
            }
        }
        if (golden != null) {
            r.setGolden(golden);
        }
        if (description != null) {
            r.setDescription(description);
        }
        Recipe saved = recipeRepository.save(r);
        audit.success("RECIPE_UPDATED", r.getCode(), r.getName());
        return toSummary(saved);
    }

    /** 环检测：沿 parent 链上溯，若遇到 {@code selfId} 则成环。 */
    private void assertNoCycle(String selfId, Recipe parent) {
        Recipe cur = parent;
        int guard = 0;
        while (cur != null && guard++ < 256) {
            if (selfId.equals(cur.getId())) {
                throw new BizException(BizCode.PARAM_INVALID, "配方层级不能成环: " + parent.getCode());
            }
            cur = cur.getParentId() == null ? null : recipeRepository.findById(cur.getParentId()).orElse(null);
        }
    }

    // ---------- 版本（FR-L1/L3，Req 13/42） ----------

    /** 新版本（Body 变更即新版本，Req 42）：版本号自动递增，状态 DRAFT。 */
    @Transactional
    public RecipeVersion newVersion(String recipeId, BodyFormat bodyFormat, String bodyBase64,
                                    String expectedBodyHash, String paramSnapshot, String changeSummary) {
        Recipe r = require(recipeId);
        RecipeVersion v = newVersionInternal(r, bodyFormat, bodyBase64, paramSnapshot, changeSummary, expectedBodyHash);
        audit.success("RECIPE_VERSION_CREATED", r.getCode(), "v" + v.getVersionNo());
        return v;
    }

    /** 激活（W1 简化：DRAFT 直激活；W2 起激活前置为签核通过）：同 Recipe 旧 ACTIVE 转 OBSOLETE。 */
    @Transactional
    public RecipeVersion activate(String recipeId, String versionId) {
        Recipe r = require(recipeId);
        RecipeVersion v = requireVersion(versionId);
        if (!v.getRecipeId().equals(recipeId)) {
            throw new BizException(BizCode.PARAM_INVALID, "版本不属于该配方: " + versionId);
        }
        if (v.getStatus() != RecipeVersionStatus.DRAFT) {
            throw new BizException(BizCode.PARAM_INVALID, "仅 DRAFT 版本可激活，当前: " + v.getStatus());
        }
        // 单生效约束（Req 14）：旧 ACTIVE 退位
        String oldActiveId = r.getActiveVersionId();
        if (oldActiveId != null) {
            versionRepository.findById(oldActiveId).ifPresent(old -> {
                if (old.getStatus() == RecipeVersionStatus.ACTIVE) {
                    old.setStatus(RecipeVersionStatus.OBSOLETE);
                    versionRepository.save(old);
                }
            });
        }
        v.setStatus(RecipeVersionStatus.ACTIVE);
        v.setActivatedAt(Instant.now());
        r.setActiveVersionId(v.getId());
        recipeRepository.save(r);
        // FR-Q3：资格随新版本继承（EXACT_VERSION 资格自动指向新版本）
        qualificationService.inheritOnActivate(recipeId, oldActiveId, v.getId());
        // W11 变更效果挂钩：手动激活同样登记（参数差异按快照键差计数）
        changeEffectService.record(recipeId, r.getCode(), oldActiveId,
                oldActiveId == null ? null : versionRepository.findById(oldActiveId)
                        .map(RecipeVersion::getVersionNo).orElse(null),
                v.getId(), v.getVersionNo(),
                snapshotKeyDiff(oldActiveId == null ? null
                        : versionRepository.findById(oldActiveId).map(RecipeVersion::getParamSnapshot).orElse(null),
                        v.getParamSnapshot()),
                v.getChangeSummary());
        audit.success("RECIPE_ACTIVATED", r.getCode(), "v" + v.getVersionNo());
        return versionRepository.save(v);
    }

    // ---------- 复制/另存为（FR-F4，Req 6） ----------

    @Transactional
    public RecipeSummary copyAs(String recipeId, String newCode, String newName) {
        Recipe src = require(recipeId);
        if (recipeRepository.findByCodeAndDeletedFalse(newCode).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "配方编码已存在: " + newCode);
        }
        Recipe copy = new Recipe();
        copy.setCode(newCode);
        copy.setName(newName == null || newName.isBlank() ? src.getName() + "-副本" : newName);
        copy.setDeviceTypeId(src.getDeviceTypeId());
        copy.setAreaId(src.getAreaId());
        copy.setGolden(false); // Golden 标记不随复制传递（另存为是普通配方起点）
        copy.setDescription(src.getDescription());
        Recipe savedCopy = recipeRepository.save(copy);

        // 拷贝最新版本内容为新 Recipe 的 v1（DRAFT），记录血缘（sourceVersionId）
        Optional<RecipeVersion> latest =
                versionRepository.findFirstByRecipeIdAndDeletedFalseOrderByVersionNoDesc(src.getId());
        RecipeVersion v1 = new RecipeVersion();
        v1.setRecipeId(savedCopy.getId());
        v1.setVersionNo(1);
        v1.setStatus(RecipeVersionStatus.DRAFT);
        latest.ifPresent(lv -> {
            v1.setBodyFormat(lv.getBodyFormat());
            // 密文原样复制（不解密重加密），加密标记随行
            v1.setBodyBase64(lv.getBodyBase64());
            v1.setBodyEnc(lv.getBodyEnc());
            v1.setBodyHash(lv.getBodyHash());
            v1.setParamSnapshot(lv.getParamSnapshot());
            v1.setSourceVersionId(lv.getId());
        });
        v1.setChangeSummary("另存为自 " + src.getCode() + " v"
                + latest.map(RecipeVersion::getVersionNo).orElse(0));
        versionRepository.save(v1);
        log.info("[recipe] 另存为 {} <- {} v{}", newCode, src.getCode(),
                latest.map(RecipeVersion::getVersionNo).orElse(0));
        audit.success("RECIPE_COPIED", newCode, "另存为自 " + src.getCode());
        return toSummary(savedCopy);
    }

    // ---------- 删除（FR-F3，Req 5） ----------

    /** 软删（主档 + 全部版本）；存在 ACTIVE 版本时拒绝（先禁用/顶替）。 */
    @Transactional
    public void delete(String id) {
        Recipe r = require(id);
        if (r.getActiveVersionId() != null) {
            versionRepository.findById(r.getActiveVersionId()).ifPresent(v -> {
                if (v.getStatus() == RecipeVersionStatus.ACTIVE) {
                    throw new BizException(BizCode.PARAM_INVALID, "配方存在生效版本，禁止删除（先禁用）: " + r.getCode());
                }
            });
        }
        r.setDeleted(true);
        recipeRepository.save(r);
        for (RecipeVersion v : versionRepository.findByRecipeIdAndDeletedFalseOrderByVersionNoDesc(id)) {
            v.setDeleted(true);
            versionRepository.save(v);
        }
        log.info("[recipe] 删除配方 code={}", r.getCode());
        audit.success("RECIPE_DELETED", r.getCode(), r.getName());
    }

    // ---------- helpers ----------

    private RecipeVersion newVersionInternal(Recipe r, BodyFormat bodyFormat, String bodyBase64,
                                             String paramSnapshot, String changeSummary,
                                             String expectedBodyHash) {
        int nextNo = versionRepository
                .findFirstByRecipeIdAndDeletedFalseOrderByVersionNoDesc(r.getId())
                .map(RecipeVersion::getVersionNo).orElse(0) + 1;
        RecipeVersion v = new RecipeVersion();
        v.setRecipeId(r.getId());
        v.setVersionNo(nextNo);
        v.setStatus(RecipeVersionStatus.DRAFT);
        v.setBodyFormat(bodyFormat == null ? BodyFormat.TEXT : bodyFormat);
        // D9：库内密文存储；hash 对明文字节计算（IF-COM 语义不变）
        bodyStore.write(v, bodyBase64);
        v.setBodyHash(computeHash(bodyBase64));
        if (expectedBodyHash != null && !expectedBodyHash.isBlank()
                && !expectedBodyHash.equalsIgnoreCase(v.getBodyHash())) {
            throw new BizException(BizCode.PARAM_INVALID, "BodyHash 不匹配（IF-COM 完整性校验失败）: " + expectedBodyHash);
        }
        // W7：Body 解析（失败不阻断）——调用方未显式给快照时，尝试自动解析出扁平参数
        String snapshot = paramSnapshot;
        if (snapshot == null || snapshot.isBlank()) {
            snapshot = bodyParseService.snapshotJson(v.getBodyFormat(), bodyBase64);
        }
        v.setParamSnapshot(snapshot);
        v.setChangeSummary(changeSummary);
        return versionRepository.save(v);
    }

    /**
     * 编辑版本 Body（W7）。
     *
     * <p><b>受版本状态约束</b>：仅 {@link RecipeVersionStatus#DRAFT}（未生效/未进入签核）可改；
     * ACTIVE / OBSOLETE 一律拒绝并提示「另存为新版本」——这是「Body 一经保存不建议改」的
     * 不可变存档语义（Req 13/42）在接口层的落地。</p>
     */
    @Transactional
    public RecipeVersion updateVersionBody(String recipeId, String versionId, BodyFormat bodyFormat,
                                          String bodyBase64, String expectedBodyHash, String changeSummary) {
        Recipe r = require(recipeId);
        RecipeVersion v = requireVersion(versionId);
        if (!v.getRecipeId().equals(recipeId)) {
            throw new BizException(BizCode.PARAM_INVALID, "版本不属于该配方: " + versionId);
        }
        if (!v.getStatus().editable()) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "版本 " + v.getStatus() + " 不可编辑（仅 DRAFT 可改），请另存为新版本");
        }
        // 乐观锁：先与数据库旧值比对（IF-COM 完整性语义——防并发丢更新），再覆盖
        if (expectedBodyHash != null && !expectedBodyHash.isBlank()
                && !expectedBodyHash.equalsIgnoreCase(v.getBodyHash())) {
            throw new BizException(BizCode.PARAM_INVALID, "BodyHash 不匹配（IF-COM 完整性校验失败）: " + expectedBodyHash);
        }
        if (bodyFormat != null) {
            v.setBodyFormat(bodyFormat);
        }
        // D9：库内密文存储；hash 对明文字节计算（IF-COM 语义不变）
        bodyStore.write(v, bodyBase64);
        v.setBodyHash(computeHash(bodyBase64));
        v.setParamSnapshot(bodyParseService.snapshotJson(v.getBodyFormat(), bodyBase64));
        if (changeSummary != null && !changeSummary.isBlank()) {
            v.setChangeSummary(changeSummary);
        }
        RecipeVersion saved = versionRepository.save(v);
        audit.success("RECIPE_VERSION_EDITED", r.getCode(), "v" + v.getVersionNo());
        return saved;
    }

    private RecipeVersion requireVersion(String versionId) {
        RecipeVersion v = versionRepository.findById(versionId).orElse(null);
        if (v == null || Boolean.TRUE.equals(v.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方版本不存在: " + versionId);
        }
        return v;
    }

    /** 两份参数快照 JSON 的「键差」计数（缺/多/值变各计 1；解析失败计 0）。 */
    private int snapshotKeyDiff(String oldJson, String newJson) {
        if (oldJson == null || oldJson.isBlank() || newJson == null || newJson.isBlank()) {
            return 0;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> a = om.readValue(oldJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            Map<String, Object> b = om.readValue(newJson,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            int diff = 0;
            java.util.Set<String> keys = new java.util.HashSet<>(a.keySet());
            keys.addAll(b.keySet());
            for (String k : keys) {
                if (!java.util.Objects.equals(
                        a.get(k) == null ? null : String.valueOf(a.get(k)),
                        b.get(k) == null ? null : String.valueOf(b.get(k)))) {
                    diff++;
                }
            }
            return diff;
        } catch (Exception e) {
            log.debug("[recipe] 快照键差计数失败: {}", e.toString());
            return 0;
        }
    }

    /** Body SHA-256（对解码后字节；空 Body 返回 {@code null}）。 */
    static String computeHash(String bodyBase64) {
        if (bodyBase64 == null || bodyBase64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(bodyBase64);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (IllegalArgumentException e) {
            throw new BizException(BizCode.PARAM_INVALID, "BodyBase64 非法 Base64: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** 审计时间格式化（LocalDateTime → 字符串，前端直接展示）。 */
    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 实体 → 列表/详情摘要（含版本数、生效版本号等派生字段，前端契约稳定）。 */
    public RecipeSummary toSummary(Recipe r) {
        int versionCount = (int) versionRepository.countByRecipeIdAndDeletedFalse(r.getId());
        Integer activeVersionNo = null;
        if (r.getActiveVersionId() != null) {
            activeVersionNo = versionRepository.findById(r.getActiveVersionId())
                    .map(RecipeVersion::getVersionNo).orElse(null);
        }
        String parentCode = r.getParentId() == null ? null
                : recipeRepository.findById(r.getParentId()).map(Recipe::getCode).orElse(null);
        return new RecipeSummary(
                r.getId(), r.getCode(), r.getName(), r.getDeviceTypeId(), r.getAreaId(),
                r.isGolden(), r.getActiveVersionId(), activeVersionNo, versionCount,
                r.getParentId(), parentCode, r.getRecipeLevel(),
                r.getDescription(), fmt(r.getCreateTime()), fmt(r.getEventTime()));
    }

    /** 子配方列表（直接下级，Req 15）。 */
    public List<RecipeSummary> children(String parentId) {
        return recipeRepository.findByParentIdAndDeletedFalseOrderByCodeAsc(parentId)
                .stream().map(this::toSummary).toList();
    }

    /** 详情聚合（主档摘要 + 版本列表）。 */
    public record RecipeDetail(RecipeSummary recipe, List<RecipeVersion> versions) {
    }

    /** 配方列表/详情摘要（前端展示契约）。 */
    public record RecipeSummary(
            String id,
            String code,
            String name,
            String deviceTypeId,
            String areaId,
            boolean golden,
            String activeVersionId,
            Integer activeVersionNo,
            int versionCount,
            String parentId,
            String parentCode,
            int recipeLevel,
            String remark,
            String createTime,
            String updateTime) {
    }

    // 占位：保留 UTF-8 语义引用，防误改（Body 归档内容按平台约定一律 UTF-8）
    static final java.nio.charset.Charset BODY_CHARSET = StandardCharsets.UTF_8;
}
