package com.cim.rms.server.recipe;

import com.cim.rms.server.recipe.body.ParsedRecipe;
import com.cim.rms.server.recipe.body.RecipeBodyParseService;
import com.cim.rms.server.spec.ParamSpecService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配方良构 / 可执行性校验（对齐 SEMI E139 的「confirm checksum / check executable and well-formed」服务）。
 *
 * <p>两层语义，刻意分开：</p>
 * <ul>
 *   <li><b>wellFormed（良构）</b>：Body 可解码、可解析、有内容、参数键不重复 —— 「这个配方文件本身是坏的？」</li>
 *   <li><b>executable（可执行）</b>：良构 <b>且</b> 参数满足配方规格（FR-C5）—— 「这个配方能不能上机跑？」</li>
 * </ul>
 * <p>分开的意义：格式对但参数超规的配方应当被拦在激活/下发之前，而不是当作「文件损坏」。</p>
 */
@Service
@RequiredArgsConstructor
public class RecipeValidationService {

    private final RecipeVersionRepository versionRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeBodyParseService parseService;
    private final ParamSpecService specService;
    private final BodyStore bodyStore;
    private final com.cim.rms.server.template.BodyTemplateService templateService;

    /** 校验结论。{@code checks} 为规格违规项（非空即 {@code executable=false}）；
     *  {@code templateViolations} 为 Body 模板强制键违规（W10，模板 enforced 时缺键）。 */
    public record ValidationResult(String recipeId, String versionId, boolean wellFormed, boolean executable,
                                  List<String> errors, List<String> warnings,
                                  List<ParamSpecService.SpecCheck> checks,
                                  List<com.cim.rms.server.template.BodyTemplateService.TemplateViolation> templateViolations,
                                  ParsedRecipe parsed) {
    }

    public ValidationResult validate(String recipeId, String versionId) {
        RecipeVersion v = versionRepository.findById(versionId).orElse(null);
        if (v == null || Boolean.TRUE.equals(v.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方版本不存在: " + versionId);
        }
        if (!v.getRecipeId().equals(recipeId)) {
            throw new BizException(BizCode.PARAM_INVALID, "版本不属于该配方: " + versionId);
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        RecipeBodyParseService.Outcome outcome = parseService.parseOutcome(v.getBodyFormat(), bodyStore.readPlain(v));
        ParsedRecipe parsed = outcome.parsed();
        if (parsed == null) {
            errors.add("PARSE_FAIL: " + outcome.error());
        } else {
            if (parsed.warnings() != null) {
                warnings.addAll(parsed.warnings());
            }
            if (!parsed.hasParams() && parsed.steps().isEmpty()) {
                errors.add("EMPTY_CONTENT: 未解析出任何参数或步序");
            } else if (!parsed.hasParams()) {
                warnings.add("NO_PARAMS: 仅解析出步序，未发现参数");
            }
            List<String> dup = duplicateKeys(parsed);
            if (!dup.isEmpty()) {
                errors.add("DUPLICATE_PARAM_KEY: 参数键重复 " + dup);
            }
        }

        boolean wellFormed = errors.isEmpty();

        List<ParamSpecService.SpecCheck> checks = List.of();
        List<com.cim.rms.server.template.BodyTemplateService.TemplateViolation> tplViolations = List.of();
        if (wellFormed && parsed != null) {
            // W10：Body 模板强制键校验（按配方的机型命中模板；机型可从 recipe 查）
            String deviceTypeId = null;
            com.cim.rms.server.recipe.Recipe recipeEntity =
                    recipeRepository.findById(recipeId).orElse(null);
            if (recipeEntity != null) {
                deviceTypeId = recipeEntity.getDeviceTypeId();
            }
            tplViolations = parsed.hasParams()
                    ? templateService.validateKeys(deviceTypeId, parsed.flatParams())
                    : templateService.validateKeys(deviceTypeId, Map.of());
            if (!tplViolations.isEmpty()) {
                errors.add("TEMPLATE_MISSING_KEY: 模板强制键缺失 " + tplViolations.stream()
                        .map(com.cim.rms.server.template.BodyTemplateService.TemplateViolation::paramKey)
                        .toList());
            }
            if (parsed.hasParams()) {
                checks = specService.check(recipeId, parsed.flatParams());
                if (!checks.isEmpty()) {
                    errors.add("SPEC_VIOLATION: 参数超出规格 " + checks.stream()
                            .map(ParamSpecService.SpecCheck::paramKey).toList());
                }
            }
        }
        boolean executable = wellFormed && checks.isEmpty() && tplViolations.isEmpty();
        return new ValidationResult(recipeId, versionId, wellFormed, executable, errors, warnings, checks,
                tplViolations, parsed);
    }

    /** 保存前预检（W10 新建向导）：建档/改版前按 body 内容 + 机型模板先行校验，不入库。 */
    public PreviewResult previewParse(String deviceTypeId, BodyFormat bodyFormat, String bodyBase64) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        RecipeBodyParseService.Outcome outcome = parseService.parseOutcome(
                bodyFormat == null ? BodyFormat.TEXT : bodyFormat, bodyBase64);
        ParsedRecipe parsed = outcome.parsed();
        if (parsed == null) {
            errors.add("PARSE_FAIL: " + outcome.error());
        } else {
            if (parsed.warnings() != null) {
                warnings.addAll(parsed.warnings());
            }
        }
        List<com.cim.rms.server.template.BodyTemplateService.TemplateViolation> tpl =
                parsed != null && parsed.hasParams()
                        ? templateService.validateKeys(deviceTypeId, parsed.flatParams())
                        : List.of();
        if (!tpl.isEmpty()) {
            errors.add("TEMPLATE_MISSING_KEY: 模板强制键缺失 " + tpl.stream()
                    .map(com.cim.rms.server.template.BodyTemplateService.TemplateViolation::paramKey)
                    .toList());
        }
        return new PreviewResult(deviceTypeId,
                bodyFormat == null ? BodyFormat.TEXT.name() : bodyFormat.name(),
                errors.isEmpty(), errors, warnings, tpl,
                parsed == null ? null : parsed.flatParams());
    }

    /** 保存前预检结论（不落库）。 */
    public record PreviewResult(String deviceTypeId, String bodyFormat, boolean ok,
                                List<String> errors, List<String> warnings,
                                List<com.cim.rms.server.template.BodyTemplateService.TemplateViolation> templateViolations,
                                Map<String, String> params) {
    }

    private List<String> duplicateKeys(ParsedRecipe parsed) {
        Set<String> seen = new HashSet<>();
        List<String> dup = new ArrayList<>();
        for (ParsedRecipe.Param p : parsed.params()) {
            if (p.key() != null && !seen.add(p.key()) && !dup.contains(p.key())) {
                dup.add(p.key());
            }
        }
        return dup;
    }

    /** 供外部构造扁平快照（避免各处重复实现）。 */
    public static Map<String, String> flat(ParsedRecipe parsed) {
        return parsed == null ? new LinkedHashMap<>() : parsed.flatParams();
    }
}
