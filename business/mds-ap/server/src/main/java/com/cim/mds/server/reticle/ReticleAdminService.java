package com.cim.mds.server.reticle;

import java.time.LocalDate;
import java.util.List;

import com.cim.mds.server.equipment.CimEquipment;
import com.cim.mds.server.equipment.CimEquipmentRepository;
import com.cim.mds.server.flow.CimFlowStepRepository;
import com.cim.mds.server.flow.CimOperationRepository;
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
 * 光罩（Reticle）管理——光刻关键耗材级主数据 CRUD（V32）。
 *
 * <p>reticle_code 全局唯一、建后不可改；status 挂字典 mds.reticle_status（非空才校验）；
 * 存放设备必须真实存在。删除守门：被工序（reticleRef）/流程步骤（reticleRef）引用的
 * 光罩不可删——与工序删除守门同口径，引用闭环。</p>
 */
@Service
@RequiredArgsConstructor
public class ReticleAdminService {

    /** 光罩状态字典。 */
    public static final String DICT_RETICLE_STATUS = "mds.reticle_status";
    /** 晶圆尺寸 / 光罩版尺寸字典（V35 多尺寸厂）。 */
    public static final String DICT_WAFER_SIZE = "mds.wafer_size";
    public static final String DICT_RETICLE_PLATE_SIZE = "mds.reticle_plate_size";

    private final CimReticleRepository reticleRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final CimOperationRepository operationRepository;
    private final CimFlowStepRepository stepRepository;
    private final com.cim.mds.server.site.CimTechnologyRepository technologyRepository;
    private final SysDictService dictService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record ReticleView(String id, String reticleCode, String reticleName,
                              String layer, String waferSize, String plateSize, String techCode,
                              String status,
                              String homeEquipmentId, String homeEquipmentCode,
                              Integer usageCount, Integer usageLimit,
                              String expiryDate, Boolean enabled,
                              Boolean occupied, String occupiedBy) {
    }

    public record ReticleReq(String reticleCode, String reticleName, String layer,
                             String waferSize, String plateSize, String techCode,
                             String status, String homeEquipmentId,
                             Integer usageCount, Integer usageLimit,
                             String expiryDate, Boolean enabled) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<ReticleView> list(String status, String equipmentId, String keyword) {
        return reticleRepository.search(blankToNull(status), blankToNull(equipmentId),
                        blankToNull(keyword)).stream()
                .map(this::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public ReticleView create(ReticleReq req) {
        requireText(req.reticleCode(), "reticleCode");
        requireText(req.reticleName(), "reticleName");
        String code = req.reticleCode().trim();
        if (reticleRepository.existsByReticleCodeAndDeletedFalse(code)) {
            throw BizException.of(BizCode.DATA_DUPLICATE, "mds.biz.reticle.codeDuplicate", code);
        }
        requireEquipment(req.homeEquipmentId());
        requireDictItem(DICT_RETICLE_STATUS, req.status());
        CimReticle entity = new CimReticle();
        entity.setReticleCode(code);
        apply(entity, req);
        return toView(reticleRepository.save(entity));
    }

    @Transactional
    public ReticleView update(String id, ReticleReq req) {
        CimReticle entity = requireReticle(id);
        requireText(req.reticleName(), "reticleName");
        requireEquipment(req.homeEquipmentId());
        requireDictItem(DICT_RETICLE_STATUS, req.status());
        assertNotLockedByOther(entity);
        apply(entity, req);
        clearLock(entity);
        return toView(reticleRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimReticle entity = requireReticle(id);
        assertNotLockedByOther(entity);
        // 守门：被工序或流程步骤按 code 引用不可删
        if (operationRepository.existsByReticleRefAndDeletedFalse(entity.getReticleCode())
                || stepRepository.existsByReticleRefAndDeletedFalse(entity.getReticleCode())) {
            throw BizException.of(BizCode.OPERATION_NOT_ALLOWED,
                    "mds.biz.reticle.referencedNoDelete", entity.getReticleCode());
        }
        entity.setDeleted(Boolean.TRUE);
        reticleRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private void apply(CimReticle entity, ReticleReq req) {
        entity.setReticleName(req.reticleName().trim());
        entity.setLayer(trimToNull(req.layer()));
        entity.setWaferSize(requireDictItem(DICT_WAFER_SIZE, req.waferSize()));
        entity.setPlateSize(requireDictItem(DICT_RETICLE_PLATE_SIZE, req.plateSize()));
        // tech_code 引用守门（与产品 tech_code 同口径：填了必须是库内有效制程技术）
        String tech = trimToNull(req.techCode());
        if (tech != null && technologyRepository.findByTechCodeAndDeletedFalse(tech).isEmpty()) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "制程技术不存在: " + tech);
        }
        entity.setTechCode(tech);
        entity.setStatus(trimToNull(req.status()));
        entity.setHomeEquipmentId(trimToNull(req.homeEquipmentId()));
        entity.setUsageCount(req.usageCount() == null ? 0 : req.usageCount());
        entity.setUsageLimit(req.usageLimit());
        entity.setExpiryDate(req.expiryDate() == null || req.expiryDate().isBlank()
                ? null : LocalDate.parse(req.expiryDate()));
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private CimReticle requireReticle(String id) {
        return reticleRepository.findById(id)
                .filter(r -> !Boolean.TRUE.equals(r.getDeleted()))
                .orElseThrow(() -> BizException.of(BizCode.DATA_NOT_FOUND,
                        "mds.biz.reticle.notFound", id));
    }

    /** 存放设备必须真实存在（宽口径：MATERIAL 大类存储设备，不卡 equip_class）。 */
    private void requireEquipment(String equipmentId) {
        if (!StringUtils.hasText(equipmentId)) {
            return;
        }
        if (equipmentRepository.findById(equipmentId)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted())).isEmpty()) {
            throw BizException.of(BizCode.DATA_NOT_FOUND,
                    "mds.biz.reticle.equipmentNotFound", equipmentId);
        }
    }

    /** 字典值守门：非空值必须是该字典的启用 itemKey；返回归一化值（trim 后，空入空出）。 */
    private String requireDictItem(String dictCode, String value) {
        String v = trimToNull(value);
        if (v == null) {
            return null;
        }
        boolean hit = dictService.items(dictCode).stream()
                .anyMatch(i -> v.equals(i.getItemKey()));
        if (!hit) {
            throw BizException.of(BizCode.PARAM_INVALID, "mds.biz.reticle.dictInvalid",
                    dictCode, v);
        }
        return v;
    }

    private ReticleView toView(CimReticle r) {
        CimEquipment eqp = r.getHomeEquipmentId() == null
                ? null : equipmentRepository.findById(r.getHomeEquipmentId()).orElse(null);
        return new ReticleView(r.getId(), r.getReticleCode(), r.getReticleName(),
                r.getLayer(), r.getWaferSize(), r.getPlateSize(), r.getTechCode(),
                r.getStatus(), r.getHomeEquipmentId(),
                eqp == null ? null : eqp.getEquipmentCode(),
                r.getUsageCount(), r.getUsageLimit(),
                r.getExpiryDate() == null ? null : r.getExpiryDate().toString(),
                r.getEnabled(), r.getOccupied(), r.getOccupiedBy());
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
