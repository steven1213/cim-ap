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
import org.springframework.util.StringUtils;

/**
 * 机型模板（EquipmentClass）管理——Class 层主数据 CRUD（v2，2026-11-24 定稿）。
 *
 * <p>class_code 全局唯一建后不可改；kindCode 是设备大类（8 类行业定稿，页面归属依据）；
 * equipClassCode 是大类下的细类（mds.equip_class 字典管文案，从属关系由
 * {@link #KIND_CLASSES} 守门）。新增 v2 默认约束字段（片径/载具/batch/标准产能）——
 * 机台实例新建时带入、可覆盖。</p>
 *
 * <p>删除守门：被机台或机台组引用的机型不可删。</p>
 */
@Service
@RequiredArgsConstructor
public class EquipmentClassAdminService {

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

    private final CimEquipmentClassRepository classRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final CimToolGroupRepository toolGroupRepository;
    private final NamingRuleAdminService namingRuleService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record EquipmentClassView(String id, String classCode, String className,
                                    String kindCode, String equipClassCode,
                                    String defaultWaferSizes, String defaultCarrierTypes,
                                    Integer defaultBatchMin, Integer defaultBatchMax,
                                    java.math.BigDecimal defaultStandardWph,
                                    String remark, Boolean enabled, String description,
                                    Boolean occupied, String occupiedBy) {
    }

    public record EquipmentClassReq(String classCode, String className,
                                    String kindCode, String equipClassCode,
                                    String defaultWaferSizes, String defaultCarrierTypes,
                                    Integer defaultBatchMin, Integer defaultBatchMax,
                                    java.math.BigDecimal defaultStandardWph,
                                    String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<EquipmentClassView> list(String keyword) {
        return classRepository.search(blankToNull(keyword)).stream()
                .map(EquipmentClassAdminService::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public EquipmentClassView create(EquipmentClassReq req) {
        requireText(req.className(), "className");
        requireKind(req.kindCode());
        requireClassBelongsToKind(req.kindCode(), req.equipClassCode());
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_EQUIPMENT_TYPE, req.classCode(),
                Map.of("kind", req.kindCode().trim(),
                        "class", req.equipClassCode() == null ? "" : req.equipClassCode().trim()));
        if (classRepository.findByClassCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "机型编码已存在: " + code);
        }
        CimEquipmentClass entity = new CimEquipmentClass();
        entity.setClassCode(code);
        apply(entity, req);
        return toView(classRepository.save(entity));
    }

    @Transactional
    public EquipmentClassView update(String id, EquipmentClassReq req) {
        CimEquipmentClass entity = requireClass(id);
        requireText(req.className(), "className");
        requireKind(req.kindCode());
        requireClassBelongsToKind(req.kindCode(), req.equipClassCode());
        assertNotLockedByOther(entity);
        // classCode 是全局唯一主标识（机台/机台组按 code 引用）：建后不可改。
        // 注意：kindCode 变更会影响机台页面归属（如 MATERIAL→PROCESS），允许改但由使用者自担数据一致性
        entity.setClassName(req.className().trim());
        entity.setKindCode(req.kindCode());
        entity.setEquipClassCode(req.equipClassCode() == null || req.equipClassCode().isBlank()
                ? null : req.equipClassCode().trim());
        entity.setDefaultWaferSizes(normalizeCsv(req.defaultWaferSizes(), 64, "defaultWaferSizes"));
        entity.setDefaultCarrierTypes(normalizeCsv(req.defaultCarrierTypes(), 128, "defaultCarrierTypes"));
        entity.setDefaultBatchMin(req.defaultBatchMin());
        entity.setDefaultBatchMax(req.defaultBatchMax());
        entity.setDefaultStandardWph(req.defaultStandardWph());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(classRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimEquipmentClass entity = requireClass(id);
        assertNotLockedByOther(entity);
        // 守门：被机台或机台组引用的机型不可删
        if (equipmentRepository.existsByClassCodeAndDeletedFalse(entity.getClassCode())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "机型已被机台引用，先解除机台关联再删除: " + entity.getClassCode());
        }
        if (toolGroupRepository.search(null, entity.getClassCode()).stream()
                .anyMatch(g -> !Boolean.TRUE.equals(g.getDeleted()))) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "机型已被机台组绑定，先解除机台组关联再删除: " + entity.getClassCode());
        }
        entity.setDeleted(Boolean.TRUE);
        classRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimEquipmentClass requireClass(String id) {
        return classRepository.findById(id)
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "机型不存在: " + id));
    }

    private static void apply(CimEquipmentClass entity, EquipmentClassReq req) {
        entity.setClassCode(entity.getClassCode() == null ? req.classCode().trim() : entity.getClassCode());
        entity.setClassName(req.className().trim());
        entity.setKindCode(req.kindCode());
        entity.setEquipClassCode(req.equipClassCode() == null || req.equipClassCode().isBlank()
                ? null : req.equipClassCode().trim());
        entity.setDefaultWaferSizes(normalizeCsv(req.defaultWaferSizes(), 64, "defaultWaferSizes"));
        entity.setDefaultCarrierTypes(normalizeCsv(req.defaultCarrierTypes(), 128, "defaultCarrierTypes"));
        entity.setDefaultBatchMin(req.defaultBatchMin());
        entity.setDefaultBatchMax(req.defaultBatchMax());
        entity.setDefaultStandardWph(req.defaultStandardWph());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private static EquipmentClassView toView(CimEquipmentClass t) {
        return new EquipmentClassView(t.getId(), t.getClassCode(), t.getClassName(),
                t.getKindCode(), t.getEquipClassCode(),
                t.getDefaultWaferSizes(), t.getDefaultCarrierTypes(),
                t.getDefaultBatchMin(), t.getDefaultBatchMax(), t.getDefaultStandardWph(),
                t.getRemark(), t.getEnabled(), t.getDescription(),
                t.getOccupied(), t.getOccupiedBy());
    }

    /** CSV 归一：去空白、去空段、逗号重连；长度守门。 */
    private static String normalizeCsv(String raw, int maxLen, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String csv = String.join(",", java.util.Arrays.stream(raw.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        if (csv.length() > maxLen) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 过长（≤" + maxLen + " 字符）");
        }
        return csv;
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
