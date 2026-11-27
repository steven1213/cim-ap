package com.cim.mds.server.consumable;

import java.math.BigDecimal;
import java.util.List;

import com.cim.mds.server.equipment.CimEquipment;
import com.cim.mds.server.equipment.CimEquipmentRepository;
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
 * 耗材（Consumable）管理——生产辅助物料台账级主数据 CRUD（V34）。
 *
 * <p>cons_code 全局唯一、建后不可改；cons_type/unit/life_type 挂业务字典
 * （非空才校验，测试零字典依赖）；存放设备必须真实存在；warn_percent 限 (0,100]。
 * 领用/盘点流水归物料域（无反向引用表），删除不做引用守门。</p>
 */
@Service
@RequiredArgsConstructor
public class ConsumableAdminService {

    public static final String DICT_CONSUMABLE_TYPE = "mds.consumable_type";
    public static final String DICT_MEASURE_UNIT = "mds.measure_unit";
    public static final String DICT_CONSUMABLE_LIFE_TYPE = "mds.consumable_life_type";

    private final CimConsumableRepository consumableRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final SysDictService dictService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record ConsumableView(String id, String consCode, String consName, String specModel,
                                 String consType, String unit, String lifeType,
                                 BigDecimal lifeLimit, BigDecimal lifeUsed, Integer warnPercent,
                                 BigDecimal stockQty, BigDecimal minQty,
                                 String vendor, String partNo, String expiryDate,
                                 String homeEquipmentId, String homeEquipmentCode,
                                 Boolean enabled, Boolean occupied, String occupiedBy) {
    }

    public record ConsumableReq(String consCode, String consName, String specModel,
                                String consType, String unit, String lifeType,
                                BigDecimal lifeLimit, BigDecimal lifeUsed, Integer warnPercent,
                                BigDecimal stockQty, BigDecimal minQty,
                                String vendor, String partNo, String expiryDate,
                                String homeEquipmentId, Boolean enabled) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<ConsumableView> list(String consType, String equipmentId, String keyword) {
        return consumableRepository.search(blankToNull(consType), blankToNull(equipmentId),
                        blankToNull(keyword)).stream()
                .map(this::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public ConsumableView create(ConsumableReq req) {
        requireText(req.consCode(), "consCode");
        requireText(req.consName(), "consName");
        String code = req.consCode().trim();
        if (consumableRepository.existsByConsCodeAndDeletedFalse(code)) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.consumable.codeDuplicate", code);
        }
        requireEquipment(req.homeEquipmentId());
        requireDictItem(DICT_CONSUMABLE_TYPE, req.consType());
        requireDictItem(DICT_MEASURE_UNIT, req.unit());
        requireDictItem(DICT_CONSUMABLE_LIFE_TYPE, req.lifeType());
        checkWarnPercent(req.warnPercent());
        CimConsumable entity = new CimConsumable();
        entity.setConsCode(code);
        apply(entity, req);
        return toView(consumableRepository.save(entity));
    }

    @Transactional
    public ConsumableView update(String id, ConsumableReq req) {
        CimConsumable entity = requireConsumable(id);
        requireText(req.consName(), "consName");
        requireEquipment(req.homeEquipmentId());
        requireDictItem(DICT_CONSUMABLE_TYPE, req.consType());
        requireDictItem(DICT_MEASURE_UNIT, req.unit());
        requireDictItem(DICT_CONSUMABLE_LIFE_TYPE, req.lifeType());
        checkWarnPercent(req.warnPercent());
        assertNotLockedByOther(entity);
        apply(entity, req);
        clearLock(entity);
        return toView(consumableRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimConsumable entity = requireConsumable(id);
        assertNotLockedByOther(entity);
        entity.setDeleted(Boolean.TRUE);
        consumableRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private void apply(CimConsumable entity, ConsumableReq req) {
        entity.setConsName(req.consName().trim());
        entity.setSpecModel(trimToNull(req.specModel()));
        entity.setConsType(trimToNull(req.consType()));
        entity.setUnit(trimToNull(req.unit()));
        entity.setLifeType(trimToNull(req.lifeType()));
        entity.setLifeLimit(req.lifeLimit());
        entity.setLifeUsed(req.lifeUsed() == null ? BigDecimal.ZERO : req.lifeUsed());
        entity.setWarnPercent(req.warnPercent());
        entity.setStockQty(req.stockQty() == null ? BigDecimal.ZERO : req.stockQty());
        entity.setMinQty(req.minQty());
        entity.setVendor(trimToNull(req.vendor()));
        entity.setPartNo(trimToNull(req.partNo()));
        entity.setExpiryDate(req.expiryDate() == null || req.expiryDate().isBlank()
                ? null : java.time.LocalDate.parse(req.expiryDate()));
        entity.setHomeEquipmentId(trimToNull(req.homeEquipmentId()));
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private void checkWarnPercent(Integer warnPercent) {
        if (warnPercent != null && (warnPercent <= 0 || warnPercent > 100)) {
            throw BizException.of(BizCode.PARAM_INVALID,
                    "mds.biz.consumable.warnPercentRange", warnPercent);
        }
    }

    private CimConsumable requireConsumable(String id) {
        return consumableRepository.findById(id)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.consumable.notFound", id));
    }

    /** 存放设备必须真实存在（宽口径：MATERIAL 大类存储设备，不卡 equip_class）。 */
    private void requireEquipment(String equipmentId) {
        if (!StringUtils.hasText(equipmentId)) {
            return;
        }
        if (equipmentRepository.findById(equipmentId)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted())).isEmpty()) {
            throw BizException.of(BizCode.DATA_NOT_FOUND,
                    "mds.biz.consumable.equipmentNotFound", equipmentId);
        }
    }

    private void requireDictItem(String dictCode, String value) {
        String v = trimToNull(value);
        if (v == null) {
            return;
        }
        boolean hit = dictService.items(dictCode).stream().anyMatch(i -> v.equals(i.getItemKey()));
        if (!hit) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.consumable.dictInvalid",
                    dictCode, v);
        }
    }

    private ConsumableView toView(CimConsumable c) {
        CimEquipment eqp = c.getHomeEquipmentId() == null
                ? null : equipmentRepository.findById(c.getHomeEquipmentId()).orElse(null);
        return new ConsumableView(c.getId(), c.getConsCode(), c.getConsName(), c.getSpecModel(),
                c.getConsType(), c.getUnit(), c.getLifeType(), c.getLifeLimit(), c.getLifeUsed(),
                c.getWarnPercent(), c.getStockQty(), c.getMinQty(),
                c.getVendor(), c.getPartNo(),
                c.getExpiryDate() == null ? null : c.getExpiryDate().toString(),
                c.getHomeEquipmentId(),
                eqp == null ? null : eqp.getEquipmentCode(), c.getEnabled(),
                c.getOccupied(), c.getOccupiedBy());
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
