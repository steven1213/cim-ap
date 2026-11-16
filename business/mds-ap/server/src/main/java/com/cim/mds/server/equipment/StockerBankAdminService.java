package com.cim.mds.server.equipment;

import java.util.List;

import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import com.cim.system.dict.SysDictService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stocker 存储区（Bank）管理——E88 zone 口径的主数据 CRUD。
 *
 * <p>bank_code 在所属设备内唯一、建后不可改；bank_type 挂 mds.stocker_bank_type
 * 字典（非空才校验，测试零字典依赖）；所属设备必须真实存在（MATERIAL 大类存储
 * 设备：Stocker/OHB/NTB 等，宽口径不卡 class，由使用者按业务挂）。删除守门：
 * 被转运规则引用（作为 from 或 to）的 Bank 不可删。</p>
 */
@Service
@RequiredArgsConstructor
public class StockerBankAdminService {

    /** 业务用途字典（原料/WIP/成品/不良/控片/回收/普通暂存）。 */
    public static final String DICT_BANK_TYPE = "mds.stocker_bank_type";

    private final CimStockerBankRepository bankRepository;
    private final CimBankTransferRuleRepository ruleRepository;
    private final CimEquipmentRepository equipmentRepository;
    private final SysDictService dictService;
    private final NamingRuleAdminService namingRuleService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record StockerBankView(String id, String equipmentId, String equipmentCode,
                                  String equipmentName, String bankCode, String bankName,
                                  String bankType, Integer capacity, Boolean enabled,
                                  Boolean occupied, String occupiedBy) {
    }

    public record StockerBankReq(String equipmentId, String bankCode, String bankName,
                                 String bankType, Integer capacity, Boolean enabled) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<StockerBankView> list(String equipmentId, String keyword) {
        return bankRepository.search(blankToNull(equipmentId), blankToNull(keyword)).stream()
                .map(this::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public StockerBankView create(StockerBankReq req) {
        requireText(req.bankName(), "bankName");
        CimEquipment equipment = requireEquipment(req.equipmentId());
        requireDictItem(req.bankType());
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_STOCKER_BANK, req.bankCode(),
                java.util.Map.of("eqp", equipment.getEquipmentCode()));
        if (bankRepository.existsByEquipmentIdAndBankCodeAndDeletedFalse(
                req.equipmentId(), code)) {
            throw new BizException(BizCode.DATA_DUPLICATE,
                    "该设备下存储区编码已存在: " + code);
        }
        CimStockerBank entity = new CimStockerBank();
        entity.setBankCode(code);
        apply(entity, req);
        return toView(bankRepository.save(entity));
    }

    @Transactional
    public StockerBankView update(String id, StockerBankReq req) {
        CimStockerBank entity = requireBank(id);
        requireText(req.bankName(), "bankName");
        requireDictItem(req.bankType());
        assertNotLockedByOther(entity);
        // bankCode 是设备内主标识（转运规则按 id 引用，但编码语义上建后不可改，与全库一致）
        entity.setBankName(req.bankName().trim());
        entity.setBankType(req.bankType() == null || req.bankType().isBlank()
                ? null : req.bankType().trim());
        entity.setCapacity(req.capacity());
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(bankRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimStockerBank entity = requireBank(id);
        assertNotLockedByOther(entity);
        // 守门：被转运规则引用（作为 from 或 to）不可删
        if (ruleRepository.existsByFromBankIdAndDeletedFalse(id)
                || ruleRepository.existsByToBankIdAndDeletedFalse(id)) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "存储区已被转运规则引用，先解除相关规则再删除: " + entity.getBankCode());
        }
        entity.setDeleted(Boolean.TRUE);
        bankRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimStockerBank requireBank(String id) {
        return bankRepository.findById(id)
                .filter(b -> !Boolean.TRUE.equals(b.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "存储区不存在: " + id));
    }

    /** 所属设备必须真实存在（宽口径：MATERIAL 大类存储设备，不卡 equip_class）。 */
    private CimEquipment requireEquipment(String equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .filter(e -> !Boolean.TRUE.equals(e.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "所属设备不存在: " + equipmentId));
    }

    private void apply(CimStockerBank entity, StockerBankReq req) {
        entity.setEquipmentId(req.equipmentId());
        entity.setBankCode(entity.getBankCode() == null ? req.bankCode().trim() : entity.getBankCode());
        entity.setBankName(req.bankName().trim());
        // bankType 在 create 里走 requireDictItem（字典校验依赖实例方法，不能进静态 apply）
        entity.setCapacity(req.capacity());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private void requireDictItem(String value) {
        String v = value == null || value.isBlank() ? null : value.trim();
        if (v == null) {
            return;
        }
        boolean hit = dictService.items(DICT_BANK_TYPE).stream().anyMatch(i -> v.equals(i.getItemKey()));
        if (!hit) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "bankType 不在数据字典 " + DICT_BANK_TYPE + " 内: " + v);
        }
    }

    private StockerBankView toView(CimStockerBank b) {
        CimEquipment eqp = equipmentRepository.findById(b.getEquipmentId()).orElse(null);
        return new StockerBankView(b.getId(), b.getEquipmentId(),
                eqp == null ? null : eqp.getEquipmentCode(),
                eqp == null ? null : eqp.getEquipmentName(),
                b.getBankCode(), b.getBankName(), b.getBankType(), b.getCapacity(),
                b.getEnabled(), b.getOccupied(), b.getOccupiedBy());
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
