package com.cim.mds.server.inspection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * 检验标准管理——「检什么/怎么检/判多严」的静态契约 CRUD（V33）。
 *
 * <p>std_code 全局唯一、建后不可改；product_group/sampling_type/aql_level/明细
 * defect_level 挂业务字典（非空才校验，测试零字典依赖）。明细页内行编辑
 * <b>全量替换</b>维护（一次提交整张明细表）；删除标准级联清明细——同事务先删后插
 * /先删后删必 flush，防 Hibernate 队列化顺序撞唯一键。检验执行与判定归 MES/QMS
 * 运行域（无反向引用表），删除不做引用守门。</p>
 */
@Service
@RequiredArgsConstructor
public class InspectionAdminService {

    public static final String DICT_PRODUCT_GROUP = "mds.product_group";
    public static final String DICT_SAMPLING_TYPE = "mds.sampling_type";
    public static final String DICT_AQL_LEVEL = "mds.aql_level";
    public static final String DICT_DEFECT_LEVEL = "mds.defect_level";

    private final CimInspectionStdRepository stdRepository;
    private final CimInspectionItemRepository itemRepository;
    private final SysDictService dictService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record ItemView(String id, String itemCode, String itemName, String spec,
                           String method, String instrument, String defectLevel, Integer sortNo) {
    }

    public record StdView(String id, String stdCode, String stdName, String productGroup,
                          String samplingType, String aqlLevel, Integer sampleQty,
                          Boolean enabled, int itemCount,
                          Boolean occupied, String occupiedBy) {
    }

    public record ItemReq(String itemCode, String itemName, String spec, String method,
                          String instrument, String defectLevel, Integer sortNo) {
    }

    public record StdReq(String stdCode, String stdName, String productGroup,
                         String samplingType, String aqlLevel, Integer sampleQty, Boolean enabled) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<StdView> list(String productGroup, String keyword) {
        return stdRepository.search(blankToNull(productGroup), blankToNull(keyword)).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ItemView> items(String stdId) {
        requireStd(stdId);
        return itemRepository.findByStdIdAndDeletedFalseOrderBySortNoAsc(stdId).stream()
                .map(InspectionAdminService::toItemView)
                .toList();
    }

    // ------------------------------------------------------------------ 写（标准头）

    @Transactional
    public StdView create(StdReq req) {
        requireText(req.stdCode(), "stdCode");
        requireText(req.stdName(), "stdName");
        String code = req.stdCode().trim();
        if (stdRepository.existsByStdCodeAndDeletedFalse(code)) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.inspection.codeDuplicate", code);
        }
        requireDictItem(DICT_PRODUCT_GROUP, req.productGroup());
        requireDictItem(DICT_SAMPLING_TYPE, req.samplingType());
        requireDictItem(DICT_AQL_LEVEL, req.aqlLevel());
        CimInspectionStd entity = new CimInspectionStd();
        entity.setStdCode(code);
        apply(entity, req);
        return toView(stdRepository.save(entity));
    }

    @Transactional
    public StdView update(String id, StdReq req) {
        CimInspectionStd entity = requireStd(id);
        requireText(req.stdName(), "stdName");
        requireDictItem(DICT_PRODUCT_GROUP, req.productGroup());
        requireDictItem(DICT_SAMPLING_TYPE, req.samplingType());
        requireDictItem(DICT_AQL_LEVEL, req.aqlLevel());
        assertNotLockedByOther(entity);
        apply(entity, req);
        clearLock(entity);
        return toView(stdRepository.save(entity));
    }

    /** 删除标准（级联清明细：先删后删必 flush，防队列化顺序撞唯一键）。 */
    @Transactional
    public void delete(String id) {
        CimInspectionStd entity = requireStd(id);
        assertNotLockedByOther(entity);
        itemRepository.findByStdIdAndDeletedFalseOrderBySortNoAsc(id)
                .forEach(item -> item.setDeleted(Boolean.TRUE));
        itemRepository.flush();
        entity.setDeleted(Boolean.TRUE);
        stdRepository.save(entity);
    }

    // ------------------------------------------------------------------ 写（明细全量替换）

    /** 全量替换标准明细（页内行编辑一次提交；先删后插必 flush）。 */
    @Transactional
    public List<ItemView> replaceItems(String stdId, List<ItemReq> items) {
        CimInspectionStd std = requireStd(stdId);
        Set<String> codes = new HashSet<>();
        for (ItemReq item : items) {
            requireText(item.itemCode(), "itemCode");
            requireText(item.itemName(), "itemName");
            requireDictItem(DICT_DEFECT_LEVEL, item.defectLevel());
            if (!codes.add(item.itemCode().trim())) {
                throw BizException.of(BizCode.DATA_DUPLICATE,
                        "mds.biz.inspection.itemCodeDuplicate", item.itemCode().trim());
            }
        }
        List<CimInspectionItem> old = itemRepository.findByStdIdAndDeletedFalseOrderBySortNoAsc(stdId);
        old.forEach(i -> i.setDeleted(Boolean.TRUE));
        itemRepository.flush();
        List<CimInspectionItem> saved = new ArrayList<>();
        int sort = 0;
        for (ItemReq req : items) {
            CimInspectionItem item = new CimInspectionItem();
            item.setStdId(stdId);
            item.setItemCode(req.itemCode().trim());
            item.setItemName(req.itemName().trim());
            item.setSpec(trimToNull(req.spec()));
            item.setMethod(trimToNull(req.method()));
            item.setInstrument(trimToNull(req.instrument()));
            item.setDefectLevel(trimToNull(req.defectLevel()));
            item.setSortNo(req.sortNo() == null ? ++sort * 10 : req.sortNo());
            saved.add(itemRepository.save(item));
        }
        return saved.stream().map(InspectionAdminService::toItemView).toList();
    }

    // ------------------------------------------------------------------ 内部

    private CimInspectionStd requireStd(String id) {
        return stdRepository.findById(id)
                .filter(s -> !Boolean.TRUE.equals(s.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.inspection.notFound", id));
    }

    private void apply(CimInspectionStd entity, StdReq req) {
        entity.setStdName(req.stdName().trim());
        entity.setProductGroup(trimToNull(req.productGroup()));
        entity.setSamplingType(trimToNull(req.samplingType()));
        entity.setAqlLevel(trimToNull(req.aqlLevel()));
        entity.setSampleQty(req.sampleQty());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private void requireDictItem(String dictCode, String value) {
        String v = trimToNull(value);
        if (v == null) {
            return;
        }
        boolean hit = dictService.items(dictCode).stream().anyMatch(i -> v.equals(i.getItemKey()));
        if (!hit) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.inspection.dictInvalid",
                    dictCode, v);
        }
    }

    private StdView toView(CimInspectionStd s) {
        return new StdView(s.getId(), s.getStdCode(), s.getStdName(), s.getProductGroup(),
                s.getSamplingType(), s.getAqlLevel(), s.getSampleQty(), s.getEnabled(),
                (int) itemRepository.countByStdIdAndDeletedFalse(s.getId()),
                s.getOccupied(), s.getOccupiedBy());
    }

    private static ItemView toItemView(CimInspectionItem i) {
        return new ItemView(i.getId(), i.getItemCode(), i.getItemName(), i.getSpec(),
                i.getMethod(), i.getInstrument(), i.getDefectLevel(), i.getSortNo());
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

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", field);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
