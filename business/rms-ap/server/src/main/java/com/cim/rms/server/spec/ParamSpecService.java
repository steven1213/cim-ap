package com.cim.rms.server.spec;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 参数规格服务（FR-S1~S4、FR-C5；Req 29/32/34/35/36/39）。
 *
 * <p>职责：配方的参数规格 CRUD（含比对开关）、规格模板管理与<b>整体应用</b>、从模板/其它配方
 * （如 Golden）<b>继承</b>规格、以及把参数快照按规格集<b>逐参数判定</b>（供上传自动检查与在线比对复用）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParamSpecService {

    private final ParamSpecRepository specRepository;
    private final SpecTemplateRepository templateRepository;
    private final SpecTemplateItemRepository templateItemRepository;
    private final RmsAuditService audit;
    private final ObjectMapper objectMapper;

    // ==================== 配方规格 ====================

    public List<SpecView> list(String recipeId) {
        return specRepository.findByRecipeIdAndDeletedFalseOrderByParamKeyAsc(recipeId)
                .stream().map(ParamSpecService::toView).toList();
    }

    /** 新增或按 paramKey 更新（同配方同 key 唯一）。 */
    @Transactional
    public SpecView upsert(String recipeId, SpecInput in) {
        if (in.paramKey() == null || in.paramKey().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "参数键不能为空");
        }
        ParamSpec s = specRepository.findByRecipeIdAndParamKeyAndDeletedFalse(recipeId, in.paramKey().trim())
                .orElseGet(ParamSpec::new);
        boolean created = s.getId() == null;
        s.setRecipeId(recipeId);
        s.setParamKey(in.paramKey().trim());
        s.setParamName(in.paramName());
        s.setSpecType(SpecType.of(in.specType()).name());
        s.setDataType(in.dataType() == null || in.dataType().isBlank() ? "NUMBER" : in.dataType().toUpperCase(Locale.ROOT));
        s.setTargetValue(in.targetValue());
        s.setRangeMin(in.rangeMin());
        s.setRangeMax(in.rangeMax());
        s.setListValues(in.listValues());
        s.setTolerancePct(in.tolerancePct());
        s.setToleranceAbs(in.toleranceAbs());
        s.setUnit(in.unit());
        s.setEnabled(in.enabled() == null || in.enabled());
        ParamSpec saved = specRepository.save(s);
        audit.success(created ? "SPEC_CREATED" : "SPEC_UPDATED", null,
                "参数规格 " + saved.getParamKey() + "（" + saved.getSpecType() + "）");
        return toView(saved);
    }

    /** 切换某参数是否参与比对（Req 34）。 */
    @Transactional
    public SpecView setEnabled(String id, boolean enabled) {
        ParamSpec s = require(id);
        s.setEnabled(enabled);
        audit.success("SPEC_TOGGLED", null, "参数 " + s.getParamKey() + " 比对开关=" + enabled);
        return toView(specRepository.save(s));
    }

    @Transactional
    public void delete(String id) {
        ParamSpec s = require(id);
        s.setDeleted(true);
        specRepository.save(s);
        audit.success("SPEC_DELETED", null, "参数规格 " + s.getParamKey());
    }

    // ==================== 模板 ====================

    public List<TemplateView> listTemplates() {
        return templateRepository.findByDeletedFalseOrderByCodeAsc().stream()
                .map(t -> new TemplateView(t.getId(), t.getCode(), t.getName(), t.getTemplateVer(),
                        t.getScopeType(), t.getDescription(),
                        (int) templateItemRepository.findByTemplateIdAndDeletedFalseOrderBySortNoAsc(t.getId()).size()))
                .toList();
    }

    public List<TemplateItemView> templateItems(String templateId) {
        return templateItemRepository.findByTemplateIdAndDeletedFalseOrderBySortNoAsc(templateId)
                .stream().map(ParamSpecService::toItemView).toList();
    }

    /** 建/改模板（整包替换条目，版本 +1）。 */
    @Transactional
    public TemplateView saveTemplate(String id, TemplateInput in) {
        SpecTemplate t = id == null ? null : requireTemplate(id);
        boolean created = t == null;
        if (created) {
            t = new SpecTemplate();
            if (in.code() == null || in.code().isBlank()) {
                throw new BizException(BizCode.PARAM_INVALID, "模板编码不能为空");
            }
            if (templateRepository.findByCodeAndDeletedFalse(in.code()).isPresent()) {
                throw new BizException(BizCode.PARAM_INVALID, "模板编码已存在: " + in.code());
            }
            t.setCode(in.code());
            t.setTemplateVer(1);
        } else if (in.code() != null && !in.code().isBlank() && !in.code().equals(t.getCode())) {
            if (templateRepository.findByCodeAndDeletedFalse(in.code()).isPresent()) {
                throw new BizException(BizCode.PARAM_INVALID, "模板编码已存在: " + in.code());
            }
            t.setCode(in.code());
        }
        t.setName(in.name() == null || in.name().isBlank() ? t.getCode() : in.name());
        t.setScopeType(in.scopeType());
        t.setDescription(in.description());
        if (!created) {
            t.setTemplateVer(t.getTemplateVer() + 1);
        }
        SpecTemplate saved = templateRepository.save(t);
        if (in.items() != null) {
            templateItemRepository.deleteByTemplateId(saved.getId());
            int sort = 0;
            for (TemplateItemInput it : in.items()) {
                SpecTemplateItem e = new SpecTemplateItem();
                e.setTemplateId(saved.getId());
                e.setParamKey(it.paramKey());
                e.setParamName(it.paramName());
                e.setSpecType(SpecType.of(it.specType()).name());
                e.setDataType(it.dataType() == null || it.dataType().isBlank() ? "NUMBER" : it.dataType().toUpperCase(Locale.ROOT));
                e.setTargetValue(it.targetValue());
                e.setRangeMin(it.rangeMin());
                e.setRangeMax(it.rangeMax());
                e.setListValues(it.listValues());
                e.setTolerancePct(it.tolerancePct());
                e.setToleranceAbs(it.toleranceAbs());
                e.setUnit(it.unit());
                e.setEnabled(it.enabled() == null || it.enabled());
                e.setSortNo(sort++);
                templateItemRepository.save(e);
            }
        }
        audit.success(created ? "SPEC_TEMPLATE_CREATED" : "SPEC_TEMPLATE_UPDATED", saved.getCode(),
                "模板 " + saved.getName() + " · 条目 " + (in.items() == null ? "-" : in.items().size()));
        return new TemplateView(saved.getId(), saved.getCode(), saved.getName(), saved.getTemplateVer(),
                saved.getScopeType(), saved.getDescription(),
                (int) templateItemRepository.findByTemplateIdAndDeletedFalseOrderBySortNoAsc(saved.getId()).size());
    }

    @Transactional
    public void deleteTemplate(String id) {
        SpecTemplate t = requireTemplate(id);
        t.setDeleted(true);
        templateRepository.save(t);
        audit.success("SPEC_TEMPLATE_DELETED", t.getCode(), t.getName());
    }

    /** 把模板整体应用到配方（Req 35）：逐条 upsert（已存在同 key 则更新），回填 templateCode。 */
    @Transactional
    public int applyTemplate(String recipeId, String templateCode) {
        SpecTemplate t = templateRepository.findByCodeAndDeletedFalse(templateCode)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "模板不存在: " + templateCode));
        List<SpecTemplateItem> items = templateItemRepository.findByTemplateIdAndDeletedFalseOrderBySortNoAsc(t.getId());
        int n = 0;
        for (SpecTemplateItem it : items) {
            ParamSpec s = specRepository.findByRecipeIdAndParamKeyAndDeletedFalse(recipeId, it.getParamKey())
                    .orElseGet(ParamSpec::new);
            s.setRecipeId(recipeId);
            s.setParamKey(it.getParamKey());
            s.setParamName(it.getParamName());
            s.setSpecType(it.getSpecType());
            s.setDataType(it.getDataType());
            s.setTargetValue(it.getTargetValue());
            s.setRangeMin(it.getRangeMin());
            s.setRangeMax(it.getRangeMax());
            s.setListValues(it.getListValues());
            s.setTolerancePct(it.getTolerancePct());
            s.setToleranceAbs(it.getToleranceAbs());
            s.setUnit(it.getUnit());
            s.setEnabled(it.isEnabled());
            s.setTemplateCode(t.getCode());
            specRepository.save(s);
            n++;
        }
        audit.success("SPEC_TEMPLATE_APPLIED", t.getCode(), "应用模板到配方 " + recipeId + " · " + n + " 项");
        return n;
    }

    /** 继承规格（Req 32/FR-S3）：从源配方（如同机型的 Golden/生效配方）复制整套规格。 */
    @Transactional
    public int copyFrom(String targetRecipeId, String sourceRecipeId) {
        List<ParamSpec> src = specRepository.findByRecipeIdAndDeletedFalseOrderByParamKeyAsc(sourceRecipeId);
        int n = 0;
        for (ParamSpec o : src) {
            ParamSpec s = specRepository.findByRecipeIdAndParamKeyAndDeletedFalse(targetRecipeId, o.getParamKey())
                    .orElseGet(ParamSpec::new);
            s.setRecipeId(targetRecipeId);
            s.setParamKey(o.getParamKey());
            s.setParamName(o.getParamName());
            s.setSpecType(o.getSpecType());
            s.setDataType(o.getDataType());
            s.setTargetValue(o.getTargetValue());
            s.setRangeMin(o.getRangeMin());
            s.setRangeMax(o.getRangeMax());
            s.setListValues(o.getListValues());
            s.setTolerancePct(o.getTolerancePct());
            s.setToleranceAbs(o.getToleranceAbs());
            s.setUnit(o.getUnit());
            s.setEnabled(o.isEnabled());
            s.setTemplateCode(o.getTemplateCode());
            specRepository.save(s);
            n++;
        }
        audit.success("SPEC_INHERITED", null, "继承规格 " + n + " 项（源配方 " + sourceRecipeId + "）");
        return n;
    }

    // ==================== 逐参数判定（供上传检查 / 在线比对复用） ====================

    /** 对给定「参数 → 实际值」快照按配方规格集判定，返回<b>失败项</b>（FR-C5/FR-C4）。 */
    public List<SpecCheck> check(String recipeId, Map<String, String> actuals) {
        List<SpecCheck> out = new ArrayList<>();
        for (ParamSpec s : specRepository.findByRecipeIdAndDeletedFalseOrderByParamKeyAsc(recipeId)) {
            if (!s.isEnabled()) {
                continue;
            }
            String actual = actuals.get(s.getParamKey());
            SpecEvaluator.Verdict v = SpecEvaluator.evaluate(SpecType.of(s.getSpecType()), s.getDataType(),
                    s.getTargetValue(), s.getRangeMin(), s.getRangeMax(), s.getListValues(),
                    s.getTolerancePct(), s.getToleranceAbs(), actual);
            if (!v.pass()) {
                out.add(new SpecCheck(s.getParamKey(), v.expected(), actual, v.diffType()));
            }
        }
        return out;
    }

    /** 参数快照 JSON → 逐参数判定的失败项（供比对内核使用）。 */
    public List<SpecCheck> checkSnapshot(String recipeId, String snapshotJson) {
        Map<String, String> actuals = new LinkedHashMap<>();
        if (snapshotJson != null && !snapshotJson.isBlank()) {
            try {
                Map<String, Object> m = objectMapper.readValue(snapshotJson, new TypeReference<Map<String, Object>>() { });
                m.forEach((k, v) -> actuals.put(k, v == null ? null : v.toString()));
            } catch (Exception e) {
                log.warn("[spec] 解析参数快照失败: {}", e.toString());
            }
        }
        return check(recipeId, actuals);
    }

    // ==================== helpers ====================

    private ParamSpec require(String id) {
        ParamSpec s = specRepository.findById(id).orElse(null);
        if (s == null || Boolean.TRUE.equals(s.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "参数规格不存在: " + id);
        }
        return s;
    }

    private SpecTemplate requireTemplate(String id) {
        SpecTemplate t = templateRepository.findById(id).orElse(null);
        if (t == null || Boolean.TRUE.equals(t.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "规格模板不存在: " + id);
        }
        return t;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static SpecView toView(ParamSpec s) {
        return new SpecView(s.getId(), s.getRecipeId(), s.getParamKey(), s.getParamName(),
                s.getSpecType(), s.getDataType(), s.getTargetValue(), s.getRangeMin(), s.getRangeMax(),
                s.getListValues(), s.getTolerancePct(), s.getToleranceAbs(), s.getUnit(),
                s.isEnabled(), s.getTemplateCode());
    }

    private static TemplateItemView toItemView(SpecTemplateItem it) {
        return new TemplateItemView(it.getId(), it.getParamKey(), it.getParamName(), it.getSpecType(),
                it.getDataType(), it.getTargetValue(), it.getRangeMin(), it.getRangeMax(), it.getListValues(),
                it.getTolerancePct(), it.getToleranceAbs(), it.getUnit(), it.isEnabled(), it.getSortNo());
    }

    // ==================== 视图 / 输入 ====================

    public record SpecView(String id, String recipeId, String paramKey, String paramName,
                           String specType, String dataType, String targetValue, String rangeMin, String rangeMax,
                           String listValues, Double tolerancePct, Double toleranceAbs, String unit,
                           boolean enabled, String templateCode) {
    }

    public record SpecInput(String paramKey, String paramName, String specType, String dataType,
                            String targetValue, String rangeMin, String rangeMax, String listValues,
                            Double tolerancePct, Double toleranceAbs, String unit, Boolean enabled) {
    }

    public record TemplateView(String id, String code, String name, int templateVer,
                               String scopeType, String remark, int itemCount) {
    }

    public record TemplateItemView(String id, String paramKey, String paramName, String specType, String dataType,
                                   String targetValue, String rangeMin, String rangeMax, String listValues,
                                   Double tolerancePct, Double toleranceAbs, String unit, boolean enabled, int sortNo) {
    }

    public record TemplateInput(String code, String name, String scopeType, String description,
                                List<TemplateItemInput> items) {
    }

    public record TemplateItemInput(String paramKey, String paramName, String specType, String dataType,
                                    String targetValue, String rangeMin, String rangeMax, String listValues,
                                    Double tolerancePct, Double toleranceAbs, String unit, Boolean enabled) {
    }

    /** 单参数判定失败项。 */
    public record SpecCheck(String paramKey, String expected, String actual, String diffType) {
    }
}
