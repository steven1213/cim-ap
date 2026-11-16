package com.cim.mds.server.equipment;

import java.util.List;
import java.util.Map;

import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备类型（EquipmentType）管理——类型主数据 CRUD。
 *
 * <p>type_code 全局唯一建后不可改；kindCode 是设备大类（8 类行业定稿，页面归属依据：
 * MATERIAL→搬运设备页，其余→设备页）；equipClassCode 是大类下的细类（mds.equip_class
 * 字典管文案，从属关系由 {@link #KIND_CLASSES} 守门——大类与细类必须是合法组合，
 * 不会出现「OHT 细类挂 PROCESS 大类」的脏数据）；删除守门：被设备引用的类型不可删。</p>
 */
@Service
@RequiredArgsConstructor
public class EquipmentTypeAdminService {

    /** 设备大类（行业口径定稿 8 类：SEMI SPE / SEAJ 设备索引 + MES 实操校准）。 */
    public static final List<String> KINDS =
            List.of("PROCESS", "METRO", "TEST", "ASSY", "MATERIAL", "FACILITY", "TOOL", "OTHER");

    /**
     * 大类→细类从属映射（与 mds.equip_class 字典同值域；MATERIAL 组含本项目 AMHS
     * 核心子系统 OHS/OHB/NTB，行业清单未列但 Auto3 必建）。页面归属由 kind 推导。
     */
    public static final Map<String, List<String>> KIND_CLASSES = Map.ofEntries(
            Map.entry("PROCESS", List.of(
                    "ETCH", "DEP", "IMP", "LITHO", "CLEAN", "THERMAL", "CMP", "PLATING", "RTP")),
            Map.entry("METRO", List.of(
                    "CDSEM", "AFM", "EL", "WAT", "INSPECT", "REVIEW", "PARTICLE")),
            Map.entry("TEST", List.of("PROBER", "TESTER", "HANDLER", "BURNIN")),
            Map.entry("ASSY", List.of("GRIND", "SAW", "DA", "WB", "MOLD", "MARK", "SING")),
            Map.entry("MATERIAL", List.of(
                    "STOCKER", "OHT", "OHS", "OHB", "NTB", "AGV", "ROBOT", "LOADER")),
            Map.entry("FACILITY", List.of(
                    "CHILLER", "PUMP", "GASBOX", "WETSCRUBBER", "FAN", "ENV", "POWER", "GASMON")),
            Map.entry("TOOL", List.of("CHUCK", "JIG", "FIXTURE")),
            Map.entry("OTHER", List.of("UNCLASS")));

    private final CimEquipmentTypeRepository typeRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final NamingRuleAdminService namingRuleService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record EquipmentTypeView(String id, String typeCode, String typeName,
                                    String kindCode, String equipClassCode,
                                    String remark, Boolean enabled, String description,
                                    Boolean occupied, String occupiedBy) {
    }

    public record EquipmentTypeReq(String typeCode, String typeName,
                                   String kindCode, String equipClassCode,
                                   String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<EquipmentTypeView> list(String keyword) {
        return typeRepository.search(blankToNull(keyword)).stream()
                .map(EquipmentTypeAdminService::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public EquipmentTypeView create(EquipmentTypeReq req) {
        requireText(req.typeName(), "typeName");
        requireKind(req.kindCode());
        requireClassBelongsToKind(req.kindCode(), req.equipClassCode());
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_EQUIPMENT_TYPE, req.typeCode(),
                Map.of("kind", req.kindCode().trim(),
                        "class", req.equipClassCode() == null ? "" : req.equipClassCode().trim()));
        if (typeRepository.findByTypeCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "设备类型编码已存在: " + code);
        }
        CimEquipmentType entity = new CimEquipmentType();
        entity.setTypeCode(code);
        apply(entity, req);
        return toView(typeRepository.save(entity));
    }

    @Transactional
    public EquipmentTypeView update(String id, EquipmentTypeReq req) {
        CimEquipmentType entity = requireType(id);
        requireText(req.typeName(), "typeName");
        requireKind(req.kindCode());
        requireClassBelongsToKind(req.kindCode(), req.equipClassCode());
        assertNotLockedByOther(entity);
        // typeCode 是全局唯一主标识（设备按 code 引用）：建后不可改。
        // 注意：kindCode 变更会影响设备页面归属（如 MATERIAL→PROCESS），允许改但由使用者自担数据一致性
        entity.setTypeName(req.typeName().trim());
        entity.setKindCode(req.kindCode());
        entity.setEquipClassCode(req.equipClassCode() == null || req.equipClassCode().isBlank()
                ? null : req.equipClassCode().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(typeRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimEquipmentType entity = requireType(id);
        assertNotLockedByOther(entity);
        // 守门：被设备引用的类型不可删
        if (equipmentRepository.existsByTypeCodeAndDeletedFalse(entity.getTypeCode())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "设备类型已被设备引用，先解除设备关联再删除: " + entity.getTypeCode());
        }
        entity.setDeleted(Boolean.TRUE);
        typeRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimEquipmentType requireType(String id) {
        return typeRepository.findById(id)
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "设备类型不存在: " + id));
    }

    private static void apply(CimEquipmentType entity, EquipmentTypeReq req) {
        entity.setTypeCode(entity.getTypeCode() == null ? req.typeCode().trim() : entity.getTypeCode());
        entity.setTypeName(req.typeName().trim());
        entity.setKindCode(req.kindCode());
        entity.setEquipClassCode(req.equipClassCode() == null || req.equipClassCode().isBlank()
                ? null : req.equipClassCode().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private static EquipmentTypeView toView(CimEquipmentType t) {
        return new EquipmentTypeView(t.getId(), t.getTypeCode(), t.getTypeName(),
                t.getKindCode(), t.getEquipClassCode(),
                t.getRemark(), t.getEnabled(), t.getDescription(),
                t.getOccupied(), t.getOccupiedBy());
    }

    private static void requireKind(String kindCode) {
        if (kindCode == null || !KINDS.contains(kindCode)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "kindCode 必须是 " + KINDS + " 之一: " + kindCode);
        }
    }

    /** 细类从属守门：细类可选，填了必须是该大类的合法细类（KIND_CLASSES 同值域字典管文案）。 */
    private static void requireClassBelongsToKind(String kindCode, String equipClassCode) {
        if (equipClassCode == null || equipClassCode.isBlank()) {
            return;
        }
        List<String> allowed = KIND_CLASSES.get(kindCode);
        String cls = equipClassCode.trim();
        if (allowed == null || !allowed.contains(cls)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "equipClassCode " + cls + " 不从属于设备大类 " + kindCode
                            + "，合法细类: " + allowed);
        }
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
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
