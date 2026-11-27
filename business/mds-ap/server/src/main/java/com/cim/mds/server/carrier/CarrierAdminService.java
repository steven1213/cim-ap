package com.cim.mds.server.carrier;

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
 * 载具（Carrier/Cassette）管理——E87 Carrier 口径的台账主数据 CRUD（V31）。
 *
 * <p>carrier_code 全局唯一、建后不可改；carrier_type/status 挂业务字典
 * （mds.carrier_type / mds.carrier_status，非空才校验，测试零字典依赖）；
 * 归属设备必须真实存在（MATERIAL 大类存储设备，宽口径不卡 class）。载具当前无
 * 下游表引用（MCS 运行态按 code 字符串取用），删除不做反向引用守门。</p>
 */
@Service
@RequiredArgsConstructor
public class CarrierAdminService {

    /** 载具类型字典。 */
    public static final String DICT_CARRIER_TYPE = "mds.carrier_type";
    /** 载具台账状态字典。 */
    public static final String DICT_CARRIER_STATUS = "mds.carrier_status";
    /** 晶圆尺寸（V35：英寸数字串，与产品/光罩/能力资格同词表）。 */
    public static final String DICT_WAFER_SIZE = "mds.wafer_size";

    private final CimCarrierRepository carrierRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final SysDictService dictService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record CarrierView(String id, String carrierCode, String carrierName,
                              String carrierType, String waferSize, Integer capacity,
                              String status,
                              String homeEquipmentId, String homeEquipmentCode,
                              String currentLotId, Integer waferQty,
                              Boolean enabled, Boolean occupied, String occupiedBy) {
    }

    public record CarrierReq(String carrierCode, String carrierName, String carrierType,
                             String waferSize, Integer capacity,
                             String status, String homeEquipmentId,
                             String currentLotId, Integer waferQty, Boolean enabled) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<CarrierView> list(String status, String equipmentId, String keyword) {
        return carrierRepository.search(blankToNull(status), blankToNull(equipmentId),
                        blankToNull(keyword)).stream()
                .map(this::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public CarrierView create(CarrierReq req) {
        requireText(req.carrierCode(), "carrierCode");
        requireText(req.carrierName(), "carrierName");
        String code = req.carrierCode().trim();
        if (carrierRepository.existsByCarrierCodeAndDeletedFalse(code)) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.carrier.codeDuplicate", code);
        }
        requireEquipment(req.homeEquipmentId());
        requireDictItem(DICT_CARRIER_TYPE, req.carrierType());
        requireDictItem(DICT_CARRIER_STATUS, req.status());
        CimCarrier entity = new CimCarrier();
        entity.setCarrierCode(code);
        apply(entity, req);
        return toView(carrierRepository.save(entity));
    }

    @Transactional
    public CarrierView update(String id, CarrierReq req) {
        CimCarrier entity = requireCarrier(id);
        requireText(req.carrierName(), "carrierName");
        requireEquipment(req.homeEquipmentId());
        requireDictItem(DICT_CARRIER_TYPE, req.carrierType());
        requireDictItem(DICT_CARRIER_STATUS, req.status());
        assertNotLockedByOther(entity);
        apply(entity, req);
        clearLock(entity);
        return toView(carrierRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimCarrier entity = requireCarrier(id);
        assertNotLockedByOther(entity);
        entity.setDeleted(Boolean.TRUE);
        carrierRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private void apply(CimCarrier entity, CarrierReq req) {
        entity.setCarrierName(req.carrierName().trim());
        entity.setCarrierType(trimToNull(req.carrierType()));
        entity.setWaferSize(requireDictItem(DICT_WAFER_SIZE, req.waferSize()));
        entity.setCapacity(req.capacity());
        entity.setStatus(trimToNull(req.status()));
        entity.setHomeEquipmentId(trimToNull(req.homeEquipmentId()));
        entity.setCurrentLotId(trimToNull(req.currentLotId()));
        entity.setWaferQty(req.waferQty());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private CimCarrier requireCarrier(String id) {
        return carrierRepository.findById(id)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.carrier.notFound", id));
    }

    /** 归属设备必须真实存在（宽口径：MATERIAL 大类存储设备，不卡 equip_class）。 */
    private void requireEquipment(String equipmentId) {
        if (!StringUtils.hasText(equipmentId)) {
            return;
        }
        if (equipmentRepository.findById(equipmentId)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted())).isEmpty()) {
            throw BizException.of(BizCode.DATA_NOT_FOUND,
                    "mds.biz.carrier.equipmentNotFound", equipmentId);
        }
    }

    /** 字典值守门：非空值必须是该字典的启用 itemKey；返回归一化值（trim 后，空入空出）。 */
    private String requireDictItem(String dictCode, String value) {
        String v = trimToNull(value);
        if (v == null) {
            return null;
        }
        boolean hit = dictService.items(dictCode).stream().anyMatch(i -> v.equals(i.getItemKey()));
        if (!hit) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.carrier.dictInvalid",
                    dictCode, v);
        }
        return v;
    }

    private CarrierView toView(CimCarrier c) {
        CimEquipment eqp = c.getHomeEquipmentId() == null
                ? null : equipmentRepository.findById(c.getHomeEquipmentId()).orElse(null);
        return new CarrierView(c.getId(), c.getCarrierCode(), c.getCarrierName(),
                c.getCarrierType(), c.getWaferSize(), c.getCapacity(),
                c.getStatus(), c.getHomeEquipmentId(),
                eqp == null ? null : eqp.getEquipmentCode(),
                c.getCurrentLotId(), c.getWaferQty(), c.getEnabled(),
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
