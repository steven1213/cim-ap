package com.cim.rms.server.template;

import com.cim.core.port.IdGenerator;
import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.recipe.BodyFormat;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Body 模板服务（W10）：模板 CRUD + 骨架下发 + 强制键校验。
 *
 * <p><b>匹配规则</b>：{@link #enforcedFor} 取机型专属的 enforced 模板；没有则回退通用
 * （scope 为空）的 enforced 模板；一个机型命中至多一个（多个时取最新，日志告警）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BodyTemplateService {

    private final BodyTemplateRepository templateRepository;
    private final BodyTemplateItemRepository itemRepository;
    private final IdGenerator idGenerator;
    private final RmsAuditService audit;

    // ==================== 查询 ====================

    public List<TemplateSummary> list(String keyword) {
        List<BodyTemplate> rows = templateRepository.findByDeletedFalseOrderByCreateTimeDesc();
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim().toLowerCase();
        List<TemplateSummary> out = new ArrayList<>();
        for (BodyTemplate t : rows) {
            TemplateSummary s = toSummary(t);
            if (kw != null) {
                String hay = (s.code() + " " + s.name()).toLowerCase();
                if (!hay.contains(kw)) {
                    continue;
                }
            }
            out.add(s);
        }
        return out;
    }

    /** 模板详情（含条目）。 */
    public TemplateDetail get(String id) {
        BodyTemplate t = require(id);
        return new TemplateDetail(toSummary(t), t.getTemplateBody(), itemsOf(id));
    }

    /**
     * 骨架下发（新建配方向导）：返回模板 Body 文本的 Base64（前端直接填入 body 输入框，
     * 与配方 create/newVersion 的 bodyBase64 契约一致）。
     */
    public Skeleton skeleton(String id) {
        BodyTemplate t = require(id);
        String text = t.getTemplateBody() == null ? "" : t.getTemplateBody();
        return new Skeleton(id, t.getCode(), t.getBodyFormat().name(),
                Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)),
                itemsOf(id));
    }

    /** 机型命中的 enforced 模板（机型专属优先，回退通用；无则 null）。 */
    public BodyTemplate enforcedFor(String deviceTypeId) {
        if (deviceTypeId != null && !deviceTypeId.isBlank()) {
            List<BodyTemplate> scoped = templateRepository
                    .findByScopeDeviceTypeIdAndDeletedFalse(deviceTypeId).stream()
                    .filter(BodyTemplate::isEnforced).toList();
            if (!scoped.isEmpty()) {
                if (scoped.size() > 1) {
                    log.warn("[btpl] 机型 {} 命中多个 enforced 模板，取最新: {}", deviceTypeId,
                            scoped.get(scoped.size() - 1).getCode());
                }
                return scoped.get(scoped.size() - 1);
            }
        }
        return templateRepository.findByEnforcedTrueAndDeletedFalse().stream()
                .filter(t -> t.getScopeDeviceTypeId() == null || t.getScopeDeviceTypeId().isBlank())
                .reduce((a, b) -> b) // 多个通用模板取最新
                .orElse(null);
    }

    // ==================== 维护 ====================

    @Transactional
    public TemplateSummary create(TemplateUpsert req) {
        if (templateRepository.findByCodeAndDeletedFalse(req.code()).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "模板编码已存在: " + req.code());
        }
        BodyTemplate t = new BodyTemplate();
        applyUpsert(t, req);
        BodyTemplate saved = templateRepository.save(t);
        replaceItems(saved.getId(), req.items());
        audit.success("BODY_TEMPLATE_CREATED", saved.getCode(), saved.getName());
        return toSummary(saved);
    }

    @Transactional
    public TemplateSummary update(String id, TemplateUpsert req) {
        BodyTemplate t = require(id);
        templateRepository.findByCodeAndDeletedFalse(req.code())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BizException(BizCode.PARAM_INVALID, "模板编码已存在: " + req.code());
                });
        applyUpsert(t, req);
        BodyTemplate saved = templateRepository.save(t);
        replaceItems(id, req.items());
        audit.success("BODY_TEMPLATE_UPDATED", saved.getCode(), saved.getName());
        return toSummary(saved);
    }

    /** 软删（主档 + 条目）。 */
    @Transactional
    public void delete(String id) {
        BodyTemplate t = require(id);
        t.setDeleted(true);
        templateRepository.save(t);
        for (BodyTemplateItem i : itemRepository.findByTemplateIdAndDeletedFalseOrderBySortNoAsc(id)) {
            i.setDeleted(true);
            itemRepository.save(i);
        }
        audit.success("BODY_TEMPLATE_DELETED", t.getCode(), t.getName());
    }

    // ==================== 强制键校验 ====================

    /**
     * 按机型命中模板校验参数键覆盖情况。
     *
     * @return 违规列表（缺 required 键）；未命中模板/模板无条目返回空表
     */
    public List<TemplateViolation> validateKeys(String deviceTypeId, Map<String, String> params) {
        BodyTemplate t = enforcedFor(deviceTypeId);
        if (t == null) {
            return List.of();
        }
        Map<String, String> actual = params == null ? Map.of() : params;
        List<TemplateViolation> out = new ArrayList<>();
        for (BodyTemplateItem i : itemRepository.findByTemplateIdAndDeletedFalseOrderBySortNoAsc(t.getId())) {
            String key = i.getParamKey();
            boolean missing = !actual.containsKey(key) || actual.get(key) == null || actual.get(key).isBlank();
            if (i.isRequired() && missing) {
                out.add(new TemplateViolation(t.getId(), t.getCode(), key, true, i.getDefaultValue(),
                        i.getUnit(), "缺少模板强制键 " + key
                                + (i.getDefaultValue() == null ? "" : "（默认值 " + i.getDefaultValue() + "）")));
            }
        }
        return out;
    }

    // ==================== helpers ====================

    private void applyUpsert(BodyTemplate t, TemplateUpsert req) {
        t.setCode(req.code());
        t.setName(req.name());
        try {
            t.setBodyFormat(req.bodyFormat() == null || req.bodyFormat().isBlank()
                    ? BodyFormat.JSON_STRUCTURED : BodyFormat.valueOf(req.bodyFormat()));
        } catch (IllegalArgumentException e) {
            throw new BizException(BizCode.PARAM_INVALID, "BodyFormat 非法: " + req.bodyFormat());
        }
        t.setScopeDeviceTypeId(req.scopeDeviceTypeId() == null || req.scopeDeviceTypeId().isBlank()
                ? null : req.scopeDeviceTypeId());
        t.setEnforced(!Boolean.FALSE.equals(req.enforced()));
        t.setTemplateBody(req.templateBody());
    }

    private void replaceItems(String templateId, List<TemplateItemUpsert> items) {
        for (BodyTemplateItem old : itemRepository.findByTemplateIdAndDeletedFalseOrderBySortNoAsc(templateId)) {
            old.setDeleted(true);
            itemRepository.save(old);
        }
        if (items == null) {
            return;
        }
        int sort = 0;
        for (TemplateItemUpsert u : items) {
            if (u.paramKey() == null || u.paramKey().isBlank()) {
                continue;
            }
            BodyTemplateItem i = new BodyTemplateItem();
            i.setTemplateId(templateId);
            i.setParamKey(u.paramKey().trim());
            i.setRequired(!Boolean.FALSE.equals(u.required()));
            i.setDefaultValue(u.defaultValue());
            i.setUnit(u.unit());
            i.setSortNo(sort++);
            itemRepository.save(i);
        }
    }

    private List<TemplateItemView> itemsOf(String templateId) {
        List<BodyTemplateItem> rows =
                itemRepository.findByTemplateIdAndDeletedFalseOrderBySortNoAsc(templateId);
        List<TemplateItemView> out = new ArrayList<>();
        for (BodyTemplateItem i : rows) {
            out.add(new TemplateItemView(i.getId(), i.getParamKey(), i.isRequired(),
                    i.getDefaultValue(), i.getUnit(), i.getSortNo()));
        }
        return out;
    }

    private BodyTemplate require(String id) {
        BodyTemplate t = templateRepository.findById(id).orElse(null);
        if (t == null || Boolean.TRUE.equals(t.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "Body 模板不存在: " + id);
        }
        return t;
    }

    private static TemplateSummary toSummary(BodyTemplate t) {
        return new TemplateSummary(t.getId(), t.getCode(), t.getName(),
                t.getBodyFormat() == null ? null : t.getBodyFormat().name(),
                t.getScopeDeviceTypeId(), t.isEnforced());
    }

    // ==================== 视图 / 输入 ====================

    public record TemplateSummary(String id, String code, String name, String bodyFormat,
                                  String scopeDeviceTypeId, boolean enforced) {
    }

    public record TemplateDetail(TemplateSummary template, String templateBody,
                                 List<TemplateItemView> items) {
    }

    public record TemplateItemView(String id, String paramKey, boolean required,
                                   String defaultValue, String unit, int sortNo) {
    }

    public record Skeleton(String id, String code, String bodyFormat, String bodyBase64,
                           List<TemplateItemView> items) {
    }

    public record TemplateViolation(String templateId, String templateCode, String paramKey,
                                    boolean required, String defaultValue, String unit, String message) {
    }

    public record TemplateUpsert(String code, String name, String bodyFormat, String scopeDeviceTypeId,
                                 Boolean enforced, String templateBody, List<TemplateItemUpsert> items) {
    }

    public record TemplateItemUpsert(String paramKey, Boolean required, String defaultValue, String unit) {
    }
}
