package com.cim.mds.server.dynamicquery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 动态查询定义管理（V39）——Query CRUD + 保存前三层验证。
 *
 * <p>保存链路（create/update 同一条）：语法 SELECT-only → 参数双向一致 →
 * 只读 dry-run（表/列真实存在）。任一层失败拒绝落库，validated_at 记录通过时间。
 * query_code 建后不可改；删除守门：被 ViewModel 引用即拒绝。</p>
 */
@Service
@RequiredArgsConstructor
public class QueryAdminService {

    private final CimQueryDefRepository defRepository;
    private final CimQueryParamRepository paramRepository;
    private final CimViewModelRepository viewModelRepository;
    private final DynamicQueryExecutor executor;

    // ------------------------------------------------------------------ 视图 / 请求

    public record ParamView(String id, String paramKey, String paramLabel, String paramType,
                            Boolean required, String dictCode, Integer sortNo) {
    }

    public record QueryView(String id, String queryCode, String queryName, String sqlText,
                            Integer pageSizeMax, Boolean enabled, LocalDateTime validatedAt,
                            List<ParamView> params,
                            Boolean occupied, String occupiedBy) {
    }

    public record ParamReq(String paramKey, String paramLabel, String paramType,
                           Boolean required, String dictCode, Integer sortNo) {
    }

    public record QueryReq(String queryCode, String queryName, String sqlText,
                           Integer pageSizeMax, Boolean enabled, List<ParamReq> params) {
    }

    private static final Set<String> PARAM_TYPES = Set.of("STRING", "NUMBER", "DATE", "DICT");

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<QueryView> list(String keyword) {
        List<QueryView> views = new ArrayList<>();
        for (CimQueryDef def : defRepository.findByDeletedFalseOrderByQueryCodeAsc()) {
            if (keyword != null && !keyword.isEmpty()
                    && !def.getQueryCode().toLowerCase().contains(keyword.toLowerCase())
                    && !def.getQueryName().toLowerCase().contains(keyword.toLowerCase())) {
                continue;
            }
            views.add(toView(def));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public QueryView get(String id) {
        return toView(requireDef(id));
    }

    /**
     * dry-run 结果集元数据（列选择下拉数据源）。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> columns(String id) {
        CimQueryDef def = requireDef(id);
        List<CimQueryParam> params = paramRepository.findByQueryIdAndDeletedFalseOrderBySortNoAsc(id);
        return executor.columns(def, params, sampleParams(params));
    }

    // ------------------------------------------------------------------ 写入

    @Transactional
    public QueryView create(QueryReq req) {
        String code = requireText(req.queryCode(), "queryCode");
        if (!code.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.codeFormat", code);
        }
        if (defRepository.existsByQueryCodeAndDeletedFalse(code)) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.dq.codeConflict", code);
        }
        requireText(req.queryName(), "queryName");
        requireText(req.sqlText(), "sqlText");

        CimQueryDef def = new CimQueryDef();
        def.setQueryCode(code);
        apply(def, req);
        validateAndPersist(def, req.params(), true);
        return toView(def);
    }

    @Transactional
    public QueryView update(String id, QueryReq req) {
        CimQueryDef def = requireDef(id);
        requireText(req.queryName(), "queryName");
        requireText(req.sqlText(), "sqlText");
        assertNotLockedByOther(def);
        apply(def, req);
        validateAndPersist(def, req.params(), false);
        clearLock(def);
        return toView(def);
    }

    @Transactional
    public void delete(String id) {
        CimQueryDef def = requireDef(id);
        List<CimViewModel> refs = viewModelRepository.findByQueryCodeAndDeletedFalse(def.getQueryCode());
        if (!refs.isEmpty()) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.dq.queryReferenced",
                    refs.get(0).getViewCode());
        }
        paramRepository.deleteByQueryId(id);
        def.setDeleted(true);
        defRepository.save(def);
    }

    // ------------------------------------------------------------------ 内部

    private void apply(CimQueryDef def, QueryReq req) {
        def.setQueryName(req.queryName().trim());
        def.setSqlText(req.sqlText().trim());
        def.setPageSizeMax(req.pageSizeMax() == null || req.pageSizeMax() < 1 || req.pageSizeMax() > 5000
                ? 500 : req.pageSizeMax());
        def.setEnabled(req.enabled() == null || req.enabled());
    }

    /**
     * 三层验证 + 落库：①②在 validator，③ dry-run；通过后保存头并全量替换参数。
     */
    private void validateAndPersist(CimQueryDef def, List<ParamReq> params, boolean create) {
        List<CimQueryParam> declared = buildAndValidateParams(def, params);
        executor.requireDryRunnable(def, declared, new LinkedHashMap<>());

        if (!create) {
            // 更新场景先落头再 dry-run 也可，但为保「验证失败不改库」，先在内存验证后统一保存
        }
        def.setValidatedAt(LocalDateTime.now());
        defRepository.save(def);

        // 参数全量替换：先删后插必须 flush（同事务删除被队列化到 INSERT 后会撞唯一键）
        paramRepository.deleteByQueryId(def.getId());
        paramRepository.flush();
        int sort = 10;
        for (CimQueryParam p : declared) {
            p.setQueryId(def.getId());
            p.setSortNo(p.getSortNo() == null || p.getSortNo() == 0 ? sort : p.getSortNo());
            sort += 10;
            paramRepository.save(p);
        }
    }

    /** 参数行合法性：键格式、类型白名单、DICT 必带字典码、声明键不重复。 */
    private List<CimQueryParam> buildAndValidateParams(CimQueryDef def, List<ParamReq> params) {
        List<CimQueryParam> list = new ArrayList<>();
        if (params == null) {
            return list;
        }
        Set<String> keys = new LinkedHashSet<>();
        for (ParamReq r : params) {
            String key = requireText(r.paramKey(), "paramKey");
            if (!key.matches("[A-Za-z][A-Za-z0-9_]*")) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.paramKeyFormat", key);
            }
            if (!keys.add(key)) {
                throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.dq.paramKeyConflict", key);
            }
            String type = r.paramType() == null ? "STRING" : r.paramType();
            if (!PARAM_TYPES.contains(type)) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.paramTypeInvalid", type);
            }
            if ("DICT".equals(type) && !StringUtils.hasText(r.dictCode())) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.paramDictRequired", key);
            }
            requireText(r.paramLabel(), "paramLabel");
            CimQueryParam p = new CimQueryParam();
            p.setParamKey(key);
            p.setParamLabel(r.paramLabel().trim());
            p.setParamType(type);
            p.setRequired(r.required() != null && r.required());
            p.setDictCode(StringUtils.hasText(r.dictCode()) ? r.dictCode().trim() : null);
            p.setSortNo(r.sortNo());
            list.add(p);
        }
        return list;
    }

    private Map<String, String> sampleParams(List<CimQueryParam> params) {
        Map<String, String> sample = new LinkedHashMap<>();
        for (CimQueryParam p : params) {
            sample.put(p.getParamKey(), switch (p.getParamType() == null ? "STRING" : p.getParamType()) {
                case "NUMBER" -> "1";
                case "DATE" -> "2000-01-01";
                default -> "";
            });
        }
        return sample;
    }

    private QueryView toView(CimQueryDef def) {
        List<ParamView> params = paramRepository
                .findByQueryIdAndDeletedFalseOrderBySortNoAsc(def.getId()).stream()
                .map(p -> new ParamView(p.getId(), p.getParamKey(), p.getParamLabel(),
                        p.getParamType(), p.getRequired(), p.getDictCode(), p.getSortNo()))
                .toList();
        return new QueryView(def.getId(), def.getQueryCode(), def.getQueryName(), def.getSqlText(),
                def.getPageSizeMax(), def.getEnabled(), def.getValidatedAt(), params,
                def.getOccupied(), def.getOccupiedBy());
    }

    private CimQueryDef requireDef(String id) {
        return defRepository.findById(id)
                .filter(d -> !Boolean.TRUE.equals(d.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.dq.queryNotFound", id));
    }

    private String requireText(String v, String field) {
        if (v == null || v.trim().isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.fieldRequired", field);
        }
        return v.trim();
    }

    private static void assertNotLockedByOther(EditLockDefData entity) {
        if (Boolean.TRUE.equals(entity.getOccupied())
                && entity.getOccupiedBy() != null
                && !entity.getOccupiedBy().equals(EditLockSupport.currentUser())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "该记录正在被 " + entity.getOccupiedBy() + " 编辑，暂不能修改或删除");
        }
    }

    private static void clearLock(EditLockDefData entity) {
        entity.setOccupied(Boolean.FALSE);
        entity.setOccupiedBy(null);
        entity.setOccupiedAt(null);
    }
}
