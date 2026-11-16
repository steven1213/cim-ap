package com.cim.mds.server.equipment;

import java.util.List;

import com.cim.mds.server.namingrule.NamingRuleAdminService;
import com.cim.mds.server.site.EditLockDefData;
import com.cim.mds.server.site.EditLockSupport;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备能力（Capability）管理——能力字典 CRUD。
 *
 * <p>capability_code 全局唯一建后不可改；删除守门：被设备关联的能力不可删。</p>
 */
@Service
@RequiredArgsConstructor
public class CapabilityAdminService {

    private final CimCapabilityRepository capabilityRepository;
    private final CimEquipmentCapabilityRepository eqpCapRepository;
    private final NamingRuleAdminService namingRuleService;

    // ------------------------------------------------------------------ 视图 / 请求

    public record CapabilityView(String id, String capabilityCode, String capabilityName,
                                 String remark, Boolean enabled, String description,
                                 Boolean occupied, String occupiedBy) {
    }

    public record CapabilityReq(String capabilityCode, String capabilityName,
                                String remark, Boolean enabled, String description) {
    }

    // ------------------------------------------------------------------ 查询

    @Transactional(readOnly = true)
    public List<CapabilityView> list(String keyword) {
        return capabilityRepository.search(blankToNull(keyword)).stream()
                .map(CapabilityAdminService::toView)
                .toList();
    }

    // ------------------------------------------------------------------ 写

    @Transactional
    public CapabilityView create(CapabilityReq req) {
        requireText(req.capabilityName(), "capabilityName");
        String code = namingRuleService.apply(NamingRuleAdminService.OBJ_CAPABILITY,
                req.capabilityCode(), java.util.Map.of());
        if (capabilityRepository.findByCapabilityCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.DATA_DUPLICATE, "能力编码已存在: " + code);
        }
        CimCapability entity = new CimCapability();
        entity.setCapabilityCode(code);
        apply(entity, req);
        return toView(capabilityRepository.save(entity));
    }

    @Transactional
    public CapabilityView update(String id, CapabilityReq req) {
        CimCapability entity = requireCapability(id);
        requireText(req.capabilityName(), "capabilityName");
        assertNotLockedByOther(entity);
        // capabilityCode 全局唯一主标识（关联表按 code 引用）：建后不可改
        entity.setCapabilityName(req.capabilityName().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
        clearLock(entity);
        return toView(capabilityRepository.save(entity));
    }

    @Transactional
    public void delete(String id) {
        CimCapability entity = requireCapability(id);
        assertNotLockedByOther(entity);
        // 守门：被设备关联的能力不可删
        if (!eqpCapRepository.findByCapabilityCodeAndDeletedFalse(entity.getCapabilityCode()).isEmpty()) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "能力已被设备关联，先解除设备关联再删除: " + entity.getCapabilityCode());
        }
        entity.setDeleted(Boolean.TRUE);
        capabilityRepository.save(entity);
    }

    // ------------------------------------------------------------------ 内部

    private CimCapability requireCapability(String id) {
        return capabilityRepository.findById(id)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "设备能力不存在: " + id));
    }

    private static void apply(CimCapability entity, CapabilityReq req) {
        entity.setCapabilityCode(entity.getCapabilityCode() == null
                ? req.capabilityCode().trim() : entity.getCapabilityCode());
        entity.setCapabilityName(req.capabilityName().trim());
        entity.setRemark(req.remark());
        entity.setDescription(req.description());
        entity.setEnabled(req.enabled() == null || req.enabled());
    }

    private static CapabilityView toView(CimCapability c) {
        return new CapabilityView(c.getId(), c.getCapabilityCode(), c.getCapabilityName(),
                c.getRemark(), c.getEnabled(), c.getDescription(), c.getOccupied(), c.getOccupiedBy());
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
