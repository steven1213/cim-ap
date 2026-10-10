package com.cim.rms.server.recipe;

import com.cim.core.port.IdGenerator;
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
import java.util.HexFormat;
import java.util.List;
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
    private final IdGenerator idGenerator;

    // ---------- 查询（FR-F2） ----------

    /** 通配符查询（FR-F2）：{@code *} 任意串、{@code ?} 单字符，其余转义。 */
    public List<Recipe> list(String keyword, String deviceTypeId, String areaId, Boolean golden) {
        String like = Wildcards.toLikePattern(keyword);
        return recipeRepository.search(like, blankToNull(deviceTypeId), blankToNull(areaId), golden,
                Sort.by(Sort.Order.asc("code")));
    }

    public Recipe require(String id) {
        Recipe r = recipeRepository.findById(id).orElse(null);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + id);
        }
        return r;
    }

    /** 详情（主档 + 全部版本，版本号倒序）。 */
    public RecipeDetail detail(String id) {
        Recipe r = require(id);
        return new RecipeDetail(r, versionsOf(id));
    }

    public List<RecipeVersion> versionsOf(String recipeId) {
        require(recipeId);
        return versionRepository.findByRecipeIdAndDeletedFalseOrderByVersionNoDesc(recipeId);
    }

    // ---------- 建档（FR-F1，Req 1/2） ----------

    @Transactional
    public Recipe create(String code, String name, String deviceTypeId, String areaId,
                         boolean golden, String description) {
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
        r.setGolden(golden);
        r.setDescription(description);
        Recipe saved = recipeRepository.save(r);
        // 初始版本 v1（DRAFT、无 Body；Body 由 newVersion/上传补齐）
        newVersionInternal(saved, null, null, null, "初始版本", null);
        log.info("[recipe] 新建配方 code={} name={} golden={}", code, name, golden);
        return saved;
    }

    /** 主档元数据维护（不触碰版本）。 */
    @Transactional
    public Recipe update(String id, String name, String deviceTypeId, String areaId,
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
        if (golden != null) {
            r.setGolden(golden);
        }
        if (description != null) {
            r.setDescription(description);
        }
        return recipeRepository.save(r);
    }

    // ---------- 版本（FR-L1/L3，Req 13/42） ----------

    /** 新版本（Body 变更即新版本，Req 42）：版本号自动递增，状态 DRAFT。 */
    @Transactional
    public RecipeVersion newVersion(String recipeId, BodyFormat bodyFormat, String bodyBase64,
                                    String expectedBodyHash, String paramSnapshot, String changeSummary) {
        Recipe r = require(recipeId);
        return newVersionInternal(r, bodyFormat, bodyBase64, paramSnapshot, changeSummary, expectedBodyHash);
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
        if (r.getActiveVersionId() != null) {
            versionRepository.findById(r.getActiveVersionId()).ifPresent(old -> {
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
        return versionRepository.save(v);
    }

    // ---------- 复制/另存为（FR-F4，Req 6） ----------

    @Transactional
    public Recipe copyAs(String recipeId, String newCode, String newName) {
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
            v1.setBodyBase64(lv.getBodyBase64());
            v1.setBodyHash(lv.getBodyHash());
            v1.setParamSnapshot(lv.getParamSnapshot());
            v1.setSourceVersionId(lv.getId());
        });
        v1.setChangeSummary("另存为自 " + src.getCode() + " v"
                + latest.map(RecipeVersion::getVersionNo).orElse(0));
        versionRepository.save(v1);
        log.info("[recipe] 另存为 {} <- {} v{}", newCode, src.getCode(),
                latest.map(RecipeVersion::getVersionNo).orElse(0));
        return savedCopy;
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
        v.setBodyBase64(bodyBase64);
        v.setBodyHash(computeHash(bodyBase64));
        if (expectedBodyHash != null && !expectedBodyHash.isBlank()
                && !expectedBodyHash.equalsIgnoreCase(v.getBodyHash())) {
            throw new BizException(BizCode.PARAM_INVALID, "BodyHash 不匹配（IF-COM 完整性校验失败）: " + expectedBodyHash);
        }
        v.setParamSnapshot(paramSnapshot);
        v.setChangeSummary(changeSummary);
        return versionRepository.save(v);
    }

    private RecipeVersion requireVersion(String versionId) {
        RecipeVersion v = versionRepository.findById(versionId).orElse(null);
        if (v == null || Boolean.TRUE.equals(v.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方版本不存在: " + versionId);
        }
        return v;
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

    /** 详情聚合（主档 + 版本列表）。 */
    public record RecipeDetail(Recipe recipe, List<RecipeVersion> versions) {
    }

    // 占位：保留 UTF-8 语义引用，防误改（Body 归档内容按平台约定一律 UTF-8）
    static final java.nio.charset.Charset BODY_CHARSET = StandardCharsets.UTF_8;
}
