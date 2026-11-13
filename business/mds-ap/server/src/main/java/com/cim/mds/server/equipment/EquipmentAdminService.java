package com.cim.mds.server.equipment;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cim.mds.server.site.CimAreaRepository;
import com.cim.mds.server.site.CimBayRepository;
import com.cim.mds.server.site.CimFabRepository;
import com.cim.mds.server.site.CimStageRepository;
import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.system.dict.SysDictService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备（Equipment）管理——实例主数据 CRUD + 能力关联维护。
 *
 * <p>一张表按类型 kind 分页视图（设备页=kind≠MATERIAL、搬运设备页=MATERIAL——
 * 行业 Process Tool / Non-Process Tool 两分法）。位置校验：MATERIAL 绑 FAB/AREA/BAY
 * （OHT 挂线区、Stocker 挂 Bay）；其余 kind 绑 STAGE（工艺/量测/测试机台绑机台位）；
 * 位置记录必须真实存在。stateCode 是状态快照
 * （mds.equip_state 字典，E10 六态口径；流转归 EAP 运行时域，此处只存当前值）。</p>
 */
@Service
@RequiredArgsConstructor
public class EquipmentAdminService {

    /** 设备可挂的位置层（组合唯一编码层用 id 引用，Fab 全局唯一也统一走 id）。 */
    private static final Set<String> LOCATION_TYPES = Set.of("FAB", "AREA", "BAY", "STAGE");

    /** 设备状态快照字典（E10 六态）。 */
    public static final String DICT_EQUIP_STATE = "mds.equip_state";

    private final CimEquipmentRepository equipmentRepository;
    private final CimEquipmentTypeRepository typeRepository;
    private final CimEquipmentCapabilityRepository eqpCapRepository;
    private final CimCapabilityRepository capabilityRepository;
    private final CimEquipmentUnitRepository unitRepository;
    private final SysDictService dictService;
    private final CimFabRepository fabRepository;
    private final CimAreaRepository areaRepository;
    private final CimBayRepository bayRepository;
    private final CimStageRepository stageRepository;

    // ------------------------------------------------------------------ 视图 / 请求

    public record EquipmentView(String id, String equipmentCode, String equipmentName,
                                String typeCode, String typeName, String typeKind,
                                String locationType, String locationId, String stateCode,
                                String remark, Boolean enabled, String description,
                                Boolean occupied, String occupiedBy) {
    }

    public record EquipmentReq(String equipmentCode, String equipmentName, String typeCode,
                               String locationType, String locationId, String stateCode,
                               String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<EquipmentView> list(String keyword, String typeCode,
                                    boolean processOnly, boolean amhsOnly) {
        Map<String, CimEquipmentType> types = typeRepository.findAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDeleted()))
                .collect(Collectors.toMap(CimEquipmentType::getTypeCode, Function.identity()));
        return equipmentRepository.search(blankToNull(keyword), blankToNull(typeCode),
                        processOnly, amhsOnly).stream()
                .map(e -> toView(e, types.get(e.getTypeCode())))
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public EquipmentView create(EquipmentReq req) {
        requireText(req.equipmentCode(), "equipmentCode");
        requireText(req.equipmentName(), "equipmentName");
        if (equipmentRepository.existsByEquipmentCodeAndDeletedFalse(req.equipmentCode().trim())) {
            throw new BizException(BizCode.DATA_DUPLICATE, "设备编码已存在: " + req.equipmentCode());
        }
        CimEquipmentType type = requireTypeByCode(req.typeCode());
        validateLocation(type, req.locationType(), req.locationId());
        CimEquipment entity = new CimEquipment();
        apply(entity, req);
        entity.setStateCode(requireDictItem(DICT_EQUIP_STATE, req.stateCode(), "stateCode"));
        return toView(equipmentRepository.save(entity), type);
    }

    @Transactional
    public EquipmentView update(String id, EquipmentReq req) {
        CimEquipment entity = requireEquipment(id);
        requireText(req.equipmentName(), "equipmentName");
        assertNotLockedByOther(entity);
        // equipmentCode 全局唯一主标识：建后不可改
        CimEquipmentType type = requireTypeByCode(req.typeCode());
        validateLocation(type, req.locationType(), req.locationId());
        entity.setEquipmentName(req.equipmentName().trim());
        entity.setTypeCode(req.typeCode());
        entity.setLocationType(req.locationType());
        entity.setLocationId(req.locationId());
        entity.setStateCode(requireDictItem(DICT_EQUIP_STATE, req.stateCode(), "stateCode"));
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(equipmentRepository.save(entity), type);
    }

    @Transactional
    public void delete(String id) {
        CimEquipment entity = requireEquipment(id);
        assertNotLockedByOther(entity);
        // 子单元随设备物理级联（软删会撞 uk_cim_equipment_unit）；能力关联同口径
        unitRepository.deleteByEquipmentId(id);
        eqpCapRepository.deleteByEquipmentId(id);
        entity.setDeleted(Boolean.TRUE);
        equipmentRepository.save(entity);
    }

    // ------------------------------------------------------------------ 能力关联

    @Transactional
    public Set<String> capabilities(String equipmentId) {
        requireEquipment(equipmentId);
        return eqpCapRepository.findByEquipmentIdAndDeletedFalse(equipmentId).stream()
                .map(CimEquipmentCapability::getCapabilityCode)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** 全量替换设备能力关联（前端勾选提交）。 */
    @Transactional
    public Set<String> setCapabilities(String equipmentId, List<String> capabilityCodes) {
        CimEquipment equipment = requireEquipment(equipmentId);
        List<String> codes = capabilityCodes == null ? List.of() : capabilityCodes.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        for (String code : codes) {
            capabilityRepository.findByCapabilityCodeAndDeletedFalse(code)
                    .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "设备能力不存在: " + code));
        }
        // 全量替换 = 物理删旧 + 插新（软删会撞 uk_cim_eqp_cap：Hibernate 先 flush insert 后 update）
        eqpCapRepository.deleteByEquipmentId(equipment.getId());
        for (String code : codes) {
            CimEquipmentCapability link = new CimEquipmentCapability();
            link.setEquipmentId(equipment.getId());
            link.setCapabilityCode(code);
            eqpCapRepository.save(link);
        }
        return new HashSet<>(codes);
    }

    // ------------------------------------------------------------------ 内部

    private CimEquipment requireEquipment(String id) {
        return equipmentRepository.findById(id)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + id));
    }

    /** 子单元域复用的包内入口（含存在性校验，不含锁守门——锁由子单元域按操作判）。 */
    CimEquipment requireForUnit(String id) {
        return requireEquipment(id);
    }

    private CimEquipmentType requireTypeByCode(String typeCode) {
        requireText(typeCode, "typeCode");
        return typeRepository.findByTypeCodeAndDeletedFalse(typeCode.trim())
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "设备类型不存在: " + typeCode));
    }

    /** 位置校验：类型合法 + kind 规则（MATERIAL→FAB/AREA/BAY，其余→STAGE）+ 记录真实存在。 */
    private void validateLocation(CimEquipmentType type, String locationType, String locationId) {
        requireText(locationType, "locationType");
        requireText(locationId, "locationId");
        if (!LOCATION_TYPES.contains(locationType)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "locationType 必须是 " + LOCATION_TYPES + " 之一: " + locationType);
        }
        boolean material = "MATERIAL".equals(type.getKindCode());
        if (material && "STAGE".equals(locationType)) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "搬运/仓储设备（" + type.getTypeCode() + "，MATERIAL 类）不能绑定位机台位(STAGE)，请选 FAB/AREA/BAY");
        }
        if (!material && !"STAGE".equals(locationType)) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "非搬运设备（" + type.getKindCode() + " 类）必须绑定位机台位(STAGE)");
        }
        boolean exists = switch (locationType) {
            case "FAB" -> fabRepository.findById(locationId).filter(f -> !Boolean.TRUE.equals(f.getDeleted())).isPresent();
            case "AREA" -> areaRepository.findById(locationId).filter(a -> !Boolean.TRUE.equals(a.getDeleted())).isPresent();
            case "BAY" -> bayRepository.findById(locationId).filter(b -> !Boolean.TRUE.equals(b.getDeleted())).isPresent();
            case "STAGE" -> stageRepository.findById(locationId).filter(s -> !Boolean.TRUE.equals(s.getDeleted())).isPresent();
            default -> false;
        };
        if (!exists) {
            throw new BizException(BizCode.DATA_NOT_FOUND,
                    "位置记录不存在: " + locationType + "/" + locationId);
        }
    }

    private static void apply(CimEquipment entity, EquipmentReq req) {
        entity.setEquipmentCode(entity.getEquipmentCode() == null
                ? req.equipmentCode().trim() : entity.getEquipmentCode());
        entity.setEquipmentName(req.equipmentName().trim());
        entity.setTypeCode(req.typeCode());
        entity.setLocationType(req.locationType());
        entity.setLocationId(req.locationId());
        // stateCode 在 create 里走 requireDictItem（字典校验依赖实例方法，不能进静态 apply）
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    /** 字典值守门：非空值必须是该字典的启用 itemKey（与 ProductAdminService 同口径）。 */
    private String requireDictItem(String dictCode, String value, String field) {
        String v = value == null || value.isBlank() ? null : value.trim();
        if (v == null) {
            return null;
        }
        boolean hit = dictService.items(dictCode).stream().anyMatch(i -> v.equals(i.getItemKey()));
        if (!hit) {
            throw new BizException(BizCode.PARAM_INVALID,
                    field + " 不在数据字典 " + dictCode + " 内: " + v);
        }
        return v;
    }

    private static EquipmentView toView(CimEquipment e, CimEquipmentType type) {
        return new EquipmentView(e.getId(), e.getEquipmentCode(), e.getEquipmentName(),
                e.getTypeCode(),
                type == null ? null : type.getTypeName(),
                type == null ? null : type.getKindCode(),
                e.getLocationType(), e.getLocationId(), e.getStateCode(),
                e.getRemark(), e.getEnabled(), e.getDescription(),
                e.getOccupied(), e.getOccupiedBy());
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
