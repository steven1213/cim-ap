package com.cim.rms.server.security;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.recipe.Recipe;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * 配方 Body 受控查看（FR-SEC2/3，行业 IP 保护）。
 *
 * <p>默认仅展示参数快照（结构化）；查看/导出<b>原始 Body</b> 需专门权限
 * （{@code rms:body:view} / {@code rms:body:export}）并全量留痕。人工鉴权见 {@link AuthorityUtil}。</p>
 */
@RestController
@RequestMapping("/api/v1/rms/versions")
@RequiredArgsConstructor
public class BodyController {

    private final RecipeVersionRepository versionRepository;
    private final RecipeRepository recipeRepository;
    private final com.cim.rms.server.recipe.BodyStore bodyStore;
    private final RmsAuditService audit;

    /** 参数快照（结构化，默认可看）。 */
    @GetMapping("/{versionId}/snapshot")
    public Result<SnapshotView> snapshot(@PathVariable String versionId) {
        RecipeVersion v = require(versionId);
        return Result.ok(new SnapshotView(v.getId(), v.getParamSnapshot(), v.getBodyHash()));
    }

    /** 原始 Body（明文，需 {@code rms:body:view}）。 */
    @GetMapping("/{versionId}/body")
    public Result<BodyView> body(@PathVariable String versionId) {
        if (!AuthorityUtil.has(RmsPermissions.BODY_VIEW)) {
            throw new BizException(BizCode.ACCESS_DENIED, "无权查看原始配方 Body（需 rms:body:view）");
        }
        RecipeVersion v = require(versionId);
        audit.success("BODY_VIEWED", recipeCode(v), "查看原始 Body v" + v.getVersionNo());
        String plain = bodyStore.readPlain(v);
        return Result.ok(new BodyView(v.getId(), v.getBodyFormat() == null ? null : v.getBodyFormat().name(),
                v.getBodyHash(), bodyLength(plain), plain));
    }

    /** 导出原始 Body（需 {@code rms:body:export}，强留痕）。 */
    @PostMapping("/{versionId}/body/export")
    public Result<BodyView> export(@PathVariable String versionId) {
        if (!AuthorityUtil.has(RmsPermissions.BODY_EXPORT)) {
            throw new BizException(BizCode.ACCESS_DENIED, "无权导出原始配方 Body（需 rms:body:export）");
        }
        RecipeVersion v = require(versionId);
        audit.success("BODY_EXPORTED", recipeCode(v), "导出原始 Body v" + v.getVersionNo());
        String plain = bodyStore.readPlain(v);
        return Result.ok(new BodyView(v.getId(), v.getBodyFormat() == null ? null : v.getBodyFormat().name(),
                v.getBodyHash(), bodyLength(plain), plain));
    }

    private RecipeVersion require(String versionId) {
        RecipeVersion v = versionRepository.findById(versionId).orElse(null);
        if (v == null || Boolean.TRUE.equals(v.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方版本不存在: " + versionId);
        }
        return v;
    }

    private String recipeCode(RecipeVersion v) {
        return recipeRepository.findById(v.getRecipeId()).map(Recipe::getCode).orElse(null);
    }

    private static int bodyLength(String b64) {
        if (b64 == null || b64.isEmpty()) {
            return 0;
        }
        try {
            return Base64.getDecoder().decode(b64).length;
        } catch (IllegalArgumentException e) {
            return b64.length();
        }
    }

    public record BodyView(String versionId, String bodyFormat, String bodyHash, int bodyLength, String bodyBase64) {
    }

    public record SnapshotView(String versionId, String paramSnapshot, String bodyHash) {
    }
}
