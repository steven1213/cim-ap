package com.cim.mds.server.dynamicquery;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.system.dict.SysDictService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 视图模型管理（V39）——UI 渲染契约 CRUD + 列元数据硬校验。
 *
 * <p>保存链路：query_code 必须存在且启用 → 列 col_key 必须在所属 Query 的
 * dry-run 结果集中（防「配置了不存在的列」）→ format_type 挂字典、DICT 列
 * 必带字典码。列调整需求在线改本域，零发版。</p>
 */
@Service
@RequiredArgsConstructor
public class ViewModelAdminService {

    public static final String DICT_FORMAT_TYPE = "mds.dq_format_type";

    private final CimViewModelRepository viewModelRepository;
    private final CimViewModelColRepository colRepository;
    private final CimQueryDefRepository queryDefRepository;
    private final CimQueryParamRepository queryParamRepository;
    private final DynamicQueryExecutor executor;
    private final SysDictService dictService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record ColView(String id, String colKey, String colTitle, String titleKey,
                          Integer width, String align, Boolean visible,
                          String formatType, String formatArg, Integer sortNo) {
    }

    public record ViewModelView(String id, String viewCode, String viewName, String queryCode,
                                String queryName, String permCode, Boolean allowExport,
                                Integer pageSize, Boolean enabled, List<ColView> cols,
                                Boolean occupied, String occupiedBy) {
    }

    public record ColReq(String colKey, String colTitle, String titleKey, Integer width,
                         String align, Boolean visible, String formatType, String formatArg,
                         Integer sortNo) {
    }

    public record ViewModelReq(String viewCode, String viewName, String queryCode, String permCode,
                               Boolean allowExport, Integer pageSize, Boolean enabled,
                               List<ColReq> cols) {
    }

    private static final Set<String> ALIGNS = Set.of("LEFT", "CENTER", "RIGHT");

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<ViewModelView> list(String keyword) {
        List<ViewModelView> views = new ArrayList<>();
        for (CimViewModel vm : viewModelRepository.findByDeletedFalseOrderByViewCodeAsc()) {
            if (keyword != null && !keyword.isEmpty()
                    && !vm.getViewCode().toLowerCase().contains(keyword.toLowerCase())
                    && !vm.getViewName().toLowerCase().contains(keyword.toLowerCase())) {
                continue;
            }
            views.add(toView(vm));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public ViewModelView get(String id) {
        return toView(requireViewModel(id));
    }

    // ------------------------------------------------------------------ 写入

    @Transactional
    public ViewModelView create(ViewModelReq req) {
        String code = requireText(req.viewCode(), "viewCode");
        if (!code.matches("[A-Za-z][A-Za0-9_-]*")) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.codeFormat", code);
        }
        if (viewModelRepository.existsByViewCodeAndDeletedFalse(code)) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.dq.codeConflict", code);
        }
        CimViewModel vm = new CimViewModel();
        vm.setViewCode(code);
        apply(vm, req);
        requireQueryBindable(vm);
        validateAndPersist(vm, req.cols(), true);
        return toView(vm);
    }

    @Transactional
    public ViewModelView update(String id, ViewModelReq req) {
        CimViewModel vm = requireViewModel(id);
        requireText(req.viewName(), "viewName");
        assertNotLockedByOther(vm);
        apply(vm, req);
        requireQueryBindable(vm);
        validateAndPersist(vm, req.cols(), false);
        clearLock(vm);
        return toView(vm);
    }

    @Transactional
    public void delete(String id) {
        CimViewModel vm = requireViewModel(id);
        colRepository.deleteByViewModelId(id);
        vm.setDeleted(true);
        viewModelRepository.save(vm);
    }

    // ------------------------------------------------------------------ 内部

    private void apply(CimViewModel vm, ViewModelReq req) {
        requireText(req.viewName(), "viewName");
        requireText(req.queryCode(), "queryCode");
        vm.setViewName(req.viewName().trim());
        vm.setQueryCode(req.queryCode().trim());
        vm.setPermCode(StringUtils.hasText(req.permCode()) ? req.permCode().trim() : null);
        vm.setAllowExport(req.allowExport() != null && req.allowExport());
        vm.setPageSize(req.pageSize() == null || req.pageSize() < 1 || req.pageSize() > 200 ? 20 : req.pageSize());
        vm.setEnabled(req.enabled() == null || req.enabled());
    }

    /** 引用的 Query 必须存在且启用。 */
    private CimQueryDef requireQueryBindable(CimViewModel vm) {
        CimQueryDef def = queryDefRepository.findByQueryCodeAndDeletedFalse(vm.getQueryCode())
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.dq.queryNotFound",
                        vm.getQueryCode()));
        if (!Boolean.TRUE.equals(def.getEnabled())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.dq.queryDisabled",
                    vm.getQueryCode());
        }
        return def;
    }

    /**
     * 列元数据硬校验 + 落库：col_key ∈ Query dry-run 结果集；format_type 挂字典；
     * DICT 列必带字典码且字典真实存在；align 白名单；col_key 行内不重复。
     */
    private void validateAndPersist(CimViewModel vm, List<ColReq> cols, boolean create) {
        CimQueryDef def = queryDefRepository.findByQueryCodeAndDeletedFalse(vm.getQueryCode())
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.dq.queryNotFound",
                        vm.getQueryCode()));
        List<CimQueryParam> queryParams =
                queryParamRepository.findByQueryIdAndDeletedFalseOrderBySortNoAsc(def.getId());
        List<Map<String, Object>> queryCols = executor.columns(def, queryParams, sampleParams(queryParams));
        Set<String> queryColKeys = new HashSet<>();
        queryCols.forEach(c -> queryColKeys.add(String.valueOf(c.get("key"))));

        List<CimViewModelCol> entities = new ArrayList<>();
        Set<String> colKeys = new HashSet<>();
        int sort = 10;
        if (cols != null) {
            for (ColReq r : cols) {
                String key = requireText(r.colKey(), "colKey");
                if (!queryColKeys.contains(key.toLowerCase())) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.colKeyInvalid",
                            key, vm.getQueryCode());
                }
                if (!colKeys.add(key.toLowerCase())) {
                    throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.dq.colKeyConflict", key);
                }
                String formatType = r.formatType() == null ? "NONE" : r.formatType();
                if (dictService.items(DICT_FORMAT_TYPE).stream().noneMatch(i -> formatType.equals(i.getItemKey()))) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.inspection.dictInvalid",
                            DICT_FORMAT_TYPE, formatType);
                }
                if ("DICT".equals(formatType) && !StringUtils.hasText(r.formatArg())) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.colDictRequired", key);
                }
                if (StringUtils.hasText(r.formatArg()) && "DICT".equals(formatType)) {
                    String dictCode = r.formatArg().trim();
                    if (dictService.items(dictCode).isEmpty()) {
                        throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.colDictNotFound",
                                key, dictCode);
                    }
                }
                String align = r.align() == null ? "LEFT" : r.align();
                if (!ALIGNS.contains(align)) {
                    throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.alignInvalid", align);
                }
                CimViewModelCol col = new CimViewModelCol();
                col.setColKey(key);
                col.setColTitle(StringUtils.hasText(r.colTitle()) ? r.colTitle().trim() : null);
                col.setTitleKey(StringUtils.hasText(r.titleKey()) ? r.titleKey().trim() : null);
                col.setWidth(r.width() == null || r.width() < 40 || r.width() > 1000 ? null : r.width());
                col.setAlign(align);
                col.setVisible(r.visible() == null || r.visible());
                col.setFormatType(formatType);
                col.setFormatArg(StringUtils.hasText(r.formatArg()) ? r.formatArg().trim() : null);
                col.setSortNo(r.sortNo() == null || r.sortNo() == 0 ? sort : r.sortNo());
                sort += 10;
                entities.add(col);
            }
        }
        if (entities.isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.dq.colsRequired");
        }

        vmRepositorySave(vm);
        colRepository.deleteByViewModelId(vm.getId());
        colRepository.flush();
        for (CimViewModelCol col : entities) {
            col.setViewModelId(vm.getId());
            colRepository.save(col);
        }
    }

    private void vmRepositorySave(CimViewModel vm) {
        viewModelRepository.save(vm);
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

    private ViewModelView toView(CimViewModel vm) {
        String queryName = queryDefRepository.findByQueryCodeAndDeletedFalse(vm.getQueryCode())
                .map(CimQueryDef::getQueryName).orElse(null);
        List<ColView> cols = colRepository
                .findByViewModelIdAndDeletedFalseOrderBySortNoAsc(vm.getId()).stream()
                .map(c -> new ColView(c.getId(), c.getColKey(), c.getColTitle(), c.getTitleKey(),
                        c.getWidth(), c.getAlign(), c.getVisible(), c.getFormatType(),
                        c.getFormatArg(), c.getSortNo()))
                .toList();
        return new ViewModelView(vm.getId(), vm.getViewCode(), vm.getViewName(), vm.getQueryCode(),
                queryName, vm.getPermCode(), vm.getAllowExport(), vm.getPageSize(), vm.getEnabled(),
                cols, vm.getOccupied(), vm.getOccupiedBy());
    }

    private CimViewModel requireViewModel(String id) {
        return viewModelRepository.findById(id)
                .filter(v -> !Boolean.TRUE.equals(v.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.dq.viewModelNotFound", id));
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
