package com.cim.mds.server.edc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cim.mds.server.flow.CimParamDef;
import com.cim.mds.server.flow.CimParamDefRepository;
import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EDC 基础主数据服务（V28）：参数集（+成员打包）、报告模板（SVID→参数显式化）
 * 与设备接口映射（机台 × CEID → 模板绑定）。
 *
 * <p>共性守门：attr_key 逻辑引用 cim_param_def——必须存在且启用（引用即依赖，
 * 与节点 KV 同口径）。参数集/报告模板编辑锁「人 vs 人」互斥（type=edcSet/
 * edcReportTpl），保存同事务清锁。</p>
 */
@Service
@RequiredArgsConstructor
public class EdcAdminService {

    private final CimEdcParamSetRepository setRepository;
    private final CimEdcParamSetItemRepository setItemRepository;
    private final CimEdcReportTplRepository tplRepository;
    private final CimEdcReportTplItemRepository tplItemRepository;
    private final CimEdcEquipMapRepository mapRepository;
    private final CimParamDefRepository defRepository;
    private final CimEdcPlanRepository planRepository;
    private final com.cim.mds.server.equipment.CimEquipmentRepository equipmentRepository;

    // ================================================================ 参数集

    public record SetReq(String setCode, String setName, String opCode, Boolean enabled, String description) {
    }

    public record SetItemReq(String attrKey, Integer sortNo) {
    }

    public record ParamLite(String attrKey, String attrName, String valueType, String unit) {
    }

    public record SetItemView(String id, String attrKey, Integer sortNo, ParamLite param) {
    }

    public record SetView(String id, String setCode, String setName, String opCode, Boolean enabled,
                          String description, Boolean occupied, String occupiedBy,
                          List<SetItemView> items) {
    }

    @Transactional(readOnly = true)
    public List<CimEdcParamSet> listSets() {
        return setRepository.findByDeletedFalseOrderByCreateTimeDesc();
    }

    @Transactional(readOnly = true)
    public SetView getSet(String id) {
        return toSetView(requireSet(id));
    }

    @Transactional
    public CimEdcParamSet createSet(SetReq req) {
        String code = requireText(req.setCode(), "setCode");
        if (setRepository.existsBySetCodeAndDeletedFalse(code)) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.edc.setDuplicate", code);
        }
        CimEdcParamSet entity = new CimEdcParamSet();
        entity.setSetCode(code); // 建后不可改
        applySetFields(entity, req);
        return setRepository.save(entity);
    }

    @Transactional
    public CimEdcParamSet updateSet(String id, SetReq req) {
        CimEdcParamSet entity = requireSet(id);
        assertNotLockedByOther(entity);
        applySetFields(entity, req);
        clearLock(entity);
        return setRepository.save(entity);
    }

    @Transactional
    public void deleteSet(String id) {
        CimEdcParamSet entity = requireSet(id);
        assertNotLockedByOther(entity);
        if (planRepository.existsBySetCodeAndDeletedFalse(entity.getSetCode())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.setReferencedNoDelete", entity.getSetCode());
        }
        // 成员物理级联（软删会撞 uk_edc_param_set_item）
        setItemRepository.deleteBySetId(id);
        entity.setDeleted(Boolean.TRUE);
        setRepository.save(entity);
    }

    /** 成员整单替换（replace-all，前端一次保存）。 */
    @Transactional
    public SetView saveSetItems(String id, List<SetItemReq> items) {
        CimEdcParamSet entity = requireSet(id);
        assertNotLockedByOther(entity);
        Set<String> seen = new HashSet<>();
        int autoSort = 0;
        List<CimEdcParamSetItem> fresh = new ArrayList<>();
        for (SetItemReq r : items == null ? List.<SetItemReq>of() : items) {
            String key = requireText(r.attrKey(), "attrKey");
            requireParam(key);
            if (!seen.add(key)) {
                throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.edc.attrDuplicate", key);
            }
            CimEdcParamSetItem item = new CimEdcParamSetItem();
            item.setSetId(id);
            item.setAttrKey(key);
            item.setSortNo(r.sortNo() == null ? autoSort : r.sortNo());
            fresh.add(item);
            autoSort++;
        }
        setItemRepository.deleteBySetId(id);
        setItemRepository.flush();
        setItemRepository.saveAll(fresh);
        clearLock(entity);
        setRepository.save(entity);
        return toSetView(entity);
    }

    /** 参数集成员原始行（供方案带入明细复用）。 */
    @Transactional(readOnly = true)
    public List<CimEdcParamSetItem> setMembers(String setId) {
        return setItemRepository.findBySetIdAndDeletedFalseOrderBySortNoAsc(setId);
    }

    private void applySetFields(CimEdcParamSet entity, SetReq req) {
        entity.setSetName(requireText(req.setName(), "setName"));
        entity.setOpCode(blankToNull(req.opCode()));
        entity.setEnabled(req.enabled() == null || req.enabled());
        entity.setDescription(req.description());
    }

    private SetView toSetView(CimEdcParamSet entity) {
        List<SetItemView> items = setItemRepository
                .findBySetIdAndDeletedFalseOrderBySortNoAsc(entity.getId()).stream()
                .map(i -> new SetItemView(i.getId(), i.getAttrKey(), i.getSortNo(), paramLite(i.getAttrKey())))
                .toList();
        return new SetView(entity.getId(), entity.getSetCode(), entity.getSetName(), entity.getOpCode(),
                entity.getEnabled(), entity.getDescription(), entity.getOccupied(), entity.getOccupiedBy(), items);
    }

    // ================================================================ 报告模板

    public record TplReq(String tplCode, String tplName, Boolean enabled, String description) {
    }

    public record TplItemReq(Integer svid, String attrKey, Integer sortNo) {
    }

    public record TplItemView(String id, Integer svid, String attrKey, Integer sortNo, ParamLite param) {
    }

    public record TplView(String id, String tplCode, String tplName, Boolean enabled,
                          String description, Boolean occupied, String occupiedBy,
                          List<TplItemView> items) {
    }

    @Transactional(readOnly = true)
    public List<CimEdcReportTpl> listTpls() {
        return tplRepository.findByDeletedFalseOrderByCreateTimeDesc();
    }

    @Transactional(readOnly = true)
    public TplView getTpl(String id) {
        return toTplView(requireTpl(id));
    }

    @Transactional
    public CimEdcReportTpl createTpl(TplReq req) {
        String code = requireText(req.tplCode(), "tplCode");
        if (tplRepository.existsByTplCodeAndDeletedFalse(code)) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.edc.tplDuplicate", code);
        }
        CimEdcReportTpl entity = new CimEdcReportTpl();
        entity.setTplCode(code); // 建后不可改
        applyTplFields(entity, req);
        return tplRepository.save(entity);
    }

    @Transactional
    public CimEdcReportTpl updateTpl(String id, TplReq req) {
        CimEdcReportTpl entity = requireTpl(id);
        assertNotLockedByOther(entity);
        applyTplFields(entity, req);
        clearLock(entity);
        return tplRepository.save(entity);
    }

    @Transactional
    public void deleteTpl(String id) {
        CimEdcReportTpl entity = requireTpl(id);
        assertNotLockedByOther(entity);
        if (mapRepository.existsByTplIdAndDeletedFalse(id)) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.edc.tplReferencedNoDelete", entity.getTplCode());
        }
        // 成员物理级联（软删会撞 uk_edc_report_tpl_item）
        tplItemRepository.deleteByTplId(id);
        entity.setDeleted(Boolean.TRUE);
        tplRepository.save(entity);
    }

    /** 模板成员整单替换（replace-all，前端一次保存）。 */
    @Transactional
    public TplView saveTplItems(String id, List<TplItemReq> items) {
        CimEdcReportTpl entity = requireTpl(id);
        assertNotLockedByOther(entity);
        Set<Integer> seenSvid = new HashSet<>();
        Set<String> seenAttr = new HashSet<>();
        int autoSort = 0;
        List<CimEdcReportTplItem> fresh = new ArrayList<>();
        for (TplItemReq r : items == null ? List.<TplItemReq>of() : items) {
            if (r.svid() == null || r.svid() <= 0) {
                throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.svidInvalid", r.svid());
            }
            String key = requireText(r.attrKey(), "attrKey");
            requireParam(key);
            if (!seenSvid.add(r.svid())) {
                throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.edc.svidDuplicate", r.svid());
            }
            if (!seenAttr.add(key)) {
                throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.edc.attrDuplicate", key);
            }
            CimEdcReportTplItem item = new CimEdcReportTplItem();
            item.setTplId(id);
            item.setSvid(r.svid());
            item.setAttrKey(key);
            item.setSortNo(r.sortNo() == null ? autoSort : r.sortNo());
            fresh.add(item);
            autoSort++;
        }
        tplItemRepository.deleteByTplId(id);
        tplItemRepository.flush();
        tplItemRepository.saveAll(fresh);
        clearLock(entity);
        tplRepository.save(entity);
        return toTplView(entity);
    }

    private void applyTplFields(CimEdcReportTpl entity, TplReq req) {
        entity.setTplName(requireText(req.tplName(), "tplName"));
        entity.setEnabled(req.enabled() == null || req.enabled());
        entity.setDescription(req.description());
    }

    private TplView toTplView(CimEdcReportTpl entity) {
        List<TplItemView> items = tplItemRepository
                .findByTplIdAndDeletedFalseOrderBySortNoAscSvidAsc(entity.getId()).stream()
                .map(i -> new TplItemView(i.getId(), i.getSvid(), i.getAttrKey(), i.getSortNo(),
                        paramLite(i.getAttrKey())))
                .toList();
        return new TplView(entity.getId(), entity.getTplCode(), entity.getTplName(), entity.getEnabled(),
                entity.getDescription(), entity.getOccupied(), entity.getOccupiedBy(), items);
    }

    private CimEdcReportTpl requireTpl(String id) {
        return tplRepository.findById(id)
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.edc.tplNotFound", id));
    }

    // ================================================================ 设备接口映射（机台 × CEID → 模板）

    public record MapReq(String equipmentCode, Integer ceid, String tplId,
                         String sourceChannel, String description) {
    }

    public record MapView(String id, String equipmentCode, Integer ceid, String tplId,
                          String tplCode, String tplName, String sourceChannel, String description) {
    }

    @Transactional(readOnly = true)
    public List<MapView> listMaps(String equipmentCode) {
        var rows = equipmentCode == null || equipmentCode.isBlank()
                ? mapRepository.findByDeletedFalseOrderByEquipmentCodeAscCeidAsc()
                : mapRepository.findByEquipmentCodeAndDeletedFalse(equipmentCode);
        return rows.stream().map(this::toMapView).toList();
    }

    @Transactional
    public CimEdcEquipMap createMap(MapReq req) {
        String equipmentCode = requireText(req.equipmentCode(), "equipmentCode");
        requireEquipment(equipmentCode);
        if (req.ceid() == null) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", "ceid");
        }
        CimEdcReportTpl tpl = requireTpl(requireText(req.tplId(), "tplId"));
        if (!Boolean.TRUE.equals(tpl.getEnabled())) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.tplDisabled", tpl.getTplCode());
        }
        if (mapRepository.findByEquipmentCodeAndCeidAndTplIdAndDeletedFalse(
                equipmentCode, req.ceid(), tpl.getId()).isPresent()) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.edc.mapDuplicate",
                    equipmentCode, req.ceid(), tpl.getTplCode());
        }
        CimEdcEquipMap entity = new CimEdcEquipMap();
        entity.setEquipmentCode(equipmentCode);
        entity.setCeid(req.ceid());
        entity.setTplId(tpl.getId());
        entity.setSourceChannel(normalizeChannel(req.sourceChannel()));
        entity.setDescription(req.description());
        return mapRepository.save(entity);
    }

    /** 映射机台须存在且启用（与候选池 EQUIP 引用同口径）。 */
    private void requireEquipment(String equipmentCode) {
        if (equipmentRepository.findByEquipmentCodeAndDeletedFalse(equipmentCode)
                .filter(e -> Boolean.TRUE.equals(e.getEnabled())).isEmpty()) {
            throw BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.edc.equipmentNotFound", equipmentCode);
        }
    }

    @Transactional
    public CimEdcEquipMap updateMap(String id, MapReq req) {
        CimEdcEquipMap entity = mapRepository.findById(id)
                .filter(m -> !Boolean.TRUE.equals(m.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.edc.mapNotFound", id));
        CimEdcReportTpl tpl = requireTpl(requireText(req.tplId(), "tplId"));
        if (!Boolean.TRUE.equals(tpl.getEnabled())) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.tplDisabled", tpl.getTplCode());
        }
        entity.setTplId(tpl.getId());
        entity.setSourceChannel(normalizeChannel(req.sourceChannel()));
        entity.setDescription(req.description());
        return mapRepository.save(entity);
    }

    @Transactional
    public void deleteMap(String id) {
        CimEdcEquipMap entity = mapRepository.findById(id)
                .filter(m -> !Boolean.TRUE.equals(m.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.edc.mapNotFound", id));
        entity.setDeleted(Boolean.TRUE);
        mapRepository.save(entity);
    }

    private MapView toMapView(CimEdcEquipMap m) {
        CimEdcReportTpl tpl = tplRepository.findById(m.getTplId())
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .orElse(null);
        return new MapView(m.getId(), m.getEquipmentCode(), m.getCeid(), m.getTplId(),
                tpl == null ? null : tpl.getTplCode(), tpl == null ? null : tpl.getTplName(),
                m.getSourceChannel(), m.getDescription());
    }

    private String normalizeChannel(String channel) {
        String c = channel == null || channel.isBlank() ? CimEdcEquipMap.CH_GEM : channel.trim();
        if (!CimEdcEquipMap.CH_GEM.equals(c) && !CimEdcEquipMap.CH_EDA.equals(c)
                && !CimEdcEquipMap.CH_FILE.equals(c)) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.channelInvalid", c);
        }
        return c;
    }

    // ================================================================ 共性

    /** 供本包其它服务/导入复用的参数存在性守门。 */
    void requireParam(String attrKey) {
        if (defRepository.findByAttrKeyAndDeletedFalse(attrKey)
                .filter(d -> Boolean.TRUE.equals(d.getEnabled()))
                .isEmpty()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.edc.attrNotFound", attrKey);
        }
    }

    ParamLite paramLite(String attrKey) {
        return defRepository.findByAttrKeyAndDeletedFalse(attrKey)
                .map(d -> new ParamLite(d.getAttrKey(), d.getAttrName(), d.getValueType(), d.getUnit()))
                .orElse(new ParamLite(attrKey, null, null, null));
    }

    private CimEdcParamSet requireSet(String id) {
        return setRepository.findById(id)
                .filter(s -> !Boolean.TRUE.equals(s.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND, "mds.biz.edc.setNotFound", id));
    }

    static void assertNotLockedByOther(EditLockDefData entity) {
        if (Boolean.TRUE.equals(entity.getOccupied())
                && entity.getOccupiedBy() != null
                && !entity.getOccupiedBy().equals(EditLockSupport.currentUser())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED, "mds.biz.edc.planLocked",
                    entity.getId(), entity.getOccupiedBy());
        }
    }

    static void clearLock(EditLockDefData entity) {
        entity.setOccupied(Boolean.FALSE);
        entity.setOccupiedBy(null);
        entity.setOccupiedAt(null);
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.common.fieldRequired", field);
        }
        return value.trim();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
