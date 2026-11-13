package com.cim.mds.server.equipment;

import java.util.List;
import java.util.Set;

import com.cim.mds.server.site.EditLockSupport;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.system.dict.SysDictService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备子单元（EquipmentUnit）管理——设备结构树节点 CRUD（SEMI E120 CEM 口径）。
 *
 * <p>unitCode 设备内唯一；unitClass/unitType 枚举校验；stateCode 挂 mds.unit_state
 * 字典（E87 端口态 / E157 腔态 / E116 EPT 合并词表，前端按 unit_type 过滤可选项）。
 * 编辑锁守门<b>父设备</b>（子单元在设备编辑上下文内维护，不设独立锁列）。</p>
 */
@Service
@RequiredArgsConstructor
public class EquipmentUnitAdminService {

    /** E120 节点类。 */
    public static final Set<String> UNIT_CLASSES = Set.of("MODULE", "SUBSYSTEM");

    /** 具体形态枚举（扩形态=加枚举值，不动表）。 */
    public static final Set<String> UNIT_TYPES =
            Set.of("LOAD_PORT", "PROCESS_CHAMBER", "ALIGNER", "ROBOT", "LOAD_LOCK", "BUFFER", "OTHER");

    /** 子单元状态快照字典。 */
    public static final String DICT_UNIT_STATE = "mds.unit_state";

    private final CimEquipmentUnitRepository unitRepository;
    private final EquipmentAdminService equipmentService;
    private final SysDictService dictService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record EquipmentUnitView(String id, String equipmentId, String unitCode, String unitName,
                                    String unitClass, String unitType, String stateCode,
                                    Integer portNo, Integer capacity, Boolean enabled, String description) {
    }

    public record EquipmentUnitReq(String unitCode, String unitName, String unitClass, String unitType,
                                   String stateCode, Integer portNo, Integer capacity,
                                   Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<EquipmentUnitView> list(String equipmentId) {
        requireEquipment(equipmentId);
        return unitRepository.findByEquipmentIdAndDeletedFalseOrderByUnitCodeAsc(equipmentId).stream()
                .map(EquipmentUnitAdminService::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public EquipmentUnitView create(String equipmentId, EquipmentUnitReq req) {
        CimEquipment equipment = requireEquipment(equipmentId);
        assertParentNotLocked(equipment);
        requireText(req.unitCode(), "unitCode");
        requireText(req.unitName(), "unitName");
        requireUnitClass(req.unitClass());
        requireUnitType(req.unitType());
        if (unitRepository.existsByEquipmentIdAndUnitCodeAndDeletedFalse(
                equipmentId, req.unitCode().trim())) {
            throw new BizException(BizCode.DATA_DUPLICATE,
                    "子单元编码在该设备内已存在: " + req.unitCode());
        }
        CimEquipmentUnit entity = new CimEquipmentUnit();
        apply(entity, req);
        entity.setEquipmentId(equipmentId);
        entity.setStateCode(requireDictItem(DICT_UNIT_STATE, req.stateCode(), "stateCode"));
        return toView(unitRepository.save(entity));
    }

    @Transactional
    public EquipmentUnitView update(String equipmentId, String unitId, EquipmentUnitReq req) {
        CimEquipment equipment = requireEquipment(equipmentId);
        assertParentNotLocked(equipment);
        CimEquipmentUnit entity = requireUnit(equipmentId, unitId);
        requireText(req.unitName(), "unitName");
        requireUnitClass(req.unitClass());
        requireUnitType(req.unitType());
        // unitCode 是设备内主标识（E120 Locator 语义）：建后不可改
        entity.setUnitName(req.unitName().trim());
        entity.setUnitClass(req.unitClass());
        entity.setUnitType(req.unitType());
        entity.setStateCode(requireDictItem(DICT_UNIT_STATE, req.stateCode(), "stateCode"));
        entity.setPortNo(req.portNo());
        entity.setCapacity(req.capacity());
        entity.setEnabled(req.enabled() == null || req.enabled());
        entity.setDescription(req.description());
        return toView(unitRepository.save(entity));
    }

    @Transactional
    public void delete(String equipmentId, String unitId) {
        CimEquipment equipment = requireEquipment(equipmentId);
        assertParentNotLocked(equipment);
        CimEquipmentUnit entity = requireUnit(equipmentId, unitId);
        entity.setDeleted(Boolean.TRUE);
        unitRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimEquipment requireEquipment(String equipmentId) {
        return equipmentService.requireForUnit(equipmentId);
    }

    private CimEquipmentUnit requireUnit(String equipmentId, String unitId) {
        return unitRepository.findById(unitId)
                .filter(u -> !Boolean.TRUE.equals(u.getDeleted()))
                .filter(u -> u.getEquipmentId().equals(equipmentId))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND,
                        "设备子单元不存在: " + unitId));
    }

    /** 编辑锁守门父设备：被他人占用即拒绝（子单元无独立锁列，与能力关联同口径）。 */
    private static void assertParentNotLocked(CimEquipment equipment) {
        if (Boolean.TRUE.equals(equipment.getOccupied())
                && equipment.getOccupiedBy() != null
                && !equipment.getOccupiedBy().equals(EditLockSupport.currentUser())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "该设备正在被 " + equipment.getOccupiedBy() + " 编辑，暂不能维护子单元");
        }
    }

    private static void apply(CimEquipmentUnit entity, EquipmentUnitReq req) {
        entity.setUnitCode(entity.getUnitCode() == null ? req.unitCode().trim() : entity.getUnitCode());
        entity.setUnitName(req.unitName().trim());
        entity.setUnitClass(req.unitClass());
        entity.setUnitType(req.unitType());
        entity.setPortNo(req.portNo());
        entity.setCapacity(req.capacity());
        entity.setEnabled(req.enabled() == null || req.enabled());
        entity.setDescription(req.description());
    }

    /** 字典值守门：非空值必须是该字典的启用 itemKey。 */
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

    private static void requireUnitClass(String unitClass) {
        if (unitClass == null || !UNIT_CLASSES.contains(unitClass)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "unitClass 必须是 " + UNIT_CLASSES + " 之一: " + unitClass);
        }
    }

    private static void requireUnitType(String unitType) {
        if (unitType == null || !UNIT_TYPES.contains(unitType)) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "unitType 必须是 " + UNIT_TYPES + " 之一: " + unitType);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, field + " 不能为空");
        }
    }

    private static EquipmentUnitView toView(CimEquipmentUnit u) {
        return new EquipmentUnitView(u.getId(), u.getEquipmentId(), u.getUnitCode(), u.getUnitName(),
                u.getUnitClass(), u.getUnitType(), u.getStateCode(),
                u.getPortNo(), u.getCapacity(), u.getEnabled(), u.getDescription());
    }
}
