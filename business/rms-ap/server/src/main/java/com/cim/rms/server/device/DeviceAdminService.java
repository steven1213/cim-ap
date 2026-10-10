package com.cim.rms.server.device;

import com.cim.rms.server.common.Wildcards;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备管理（Req 46/47/48）：设备类型 / 设备区域 / 设备台账 的基础维护。
 *
 * <p>删除一律软删（{@code deleted=true}），且做<b>引用保护</b>：
 * 类型/区域被设备引用、设备被配方引用时禁止删除；子区域存在时禁止删父区域。</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceAdminService {

    private final DeviceTypeRepository typeRepository;
    private final DeviceAreaRepository areaRepository;
    private final DeviceRepository deviceRepository;
    private final RecipeRefGuard recipeRefGuard;

    // ---------- 设备类型（Req 46） ----------

    public List<DeviceType> listTypes() {
        return typeRepository.findByDeletedFalseOrderByCode();
    }

    @Transactional
    public DeviceType createType(String code, String name, String description) {
        if (typeRepository.findByCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "机型编码已存在: " + code);
        }
        DeviceType t = new DeviceType();
        t.setCode(code);
        t.setName(name);
        t.setDescription(description);
        return typeRepository.save(t);
    }

    @Transactional
    public DeviceType updateType(String id, String name, String description) {
        DeviceType t = requireType(id);
        if (name != null && !name.isBlank()) {
            t.setName(name);
        }
        if (description != null) {
            t.setDescription(description);
        }
        return typeRepository.save(t);
    }

    @Transactional
    public void deleteType(String id) {
        DeviceType t = requireType(id);
        if (!deviceRepository.findByDeviceTypeIdAndDeletedFalse(id).isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "机型仍被设备引用，禁止删除: " + t.getCode());
        }
        t.setDeleted(true);
        typeRepository.save(t);
    }

    // ---------- 设备区域（Req 47） ----------

    public List<DeviceArea> listAreas() {
        return areaRepository.findByDeletedFalseOrderBySortNoAscCode();
    }

    @Transactional
    public DeviceArea createArea(String code, String name, String parentId, int sortNo, String description) {
        if (areaRepository.findByCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "区域编码已存在: " + code);
        }
        DeviceArea a = new DeviceArea();
        a.setCode(code);
        a.setName(name);
        a.setParentId(parentId == null || parentId.isBlank() ? null : requireArea(parentId).getId());
        a.setSortNo(sortNo);
        a.setDescription(description);
        return areaRepository.save(a);
    }

    @Transactional
    public DeviceArea updateArea(String id, String name, Integer sortNo, String description) {
        DeviceArea a = requireArea(id);
        if (name != null && !name.isBlank()) {
            a.setName(name);
        }
        if (sortNo != null) {
            a.setSortNo(sortNo);
        }
        if (description != null) {
            a.setDescription(description);
        }
        return areaRepository.save(a);
    }

    @Transactional
    public void deleteArea(String id) {
        DeviceArea a = requireArea(id);
        if (!deviceRepository.findByAreaIdAndDeletedFalse(id).isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "区域仍被设备引用，禁止删除: " + a.getCode());
        }
        List<DeviceArea> children = areaRepository.findByDeletedFalseOrderBySortNoAscCode().stream()
                .filter(x -> id.equals(x.getParentId()))
                .toList();
        if (!children.isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "区域存在子区域，禁止删除: " + a.getCode());
        }
        a.setDeleted(true);
        areaRepository.save(a);
    }

    // ---------- 设备台账（Req 48） ----------

    public List<Device> listDevices(String keyword, String deviceTypeId, String areaId, DeviceStatus status) {
        String like = Wildcards.toLikePattern(keyword);
        return deviceRepository.search(like, blankToNull(deviceTypeId), blankToNull(areaId), status,
                Sort.by(Sort.Order.asc("code")));
    }

    @Transactional
    public Device createDevice(String code, String name, String deviceTypeId, String areaId, String description) {
        if (deviceRepository.findByCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "设备编码已存在: " + code);
        }
        Device d = new Device();
        d.setCode(code);
        d.setName(name);
        d.setDeviceTypeId(requireType(deviceTypeId).getId());
        d.setAreaId(areaId == null || areaId.isBlank() ? null : requireArea(areaId).getId());
        d.setDescription(description);
        return deviceRepository.save(d);
    }

    @Transactional
    public Device updateDevice(String id, String name, String areaId, DeviceStatus status, String description) {
        Device d = requireDevice(id);
        if (name != null && !name.isBlank()) {
            d.setName(name);
        }
        if (areaId != null) {
            d.setAreaId(areaId.isBlank() ? null : requireArea(areaId).getId());
        }
        if (status != null) {
            d.setStatus(status);
        }
        if (description != null) {
            d.setDescription(description);
        }
        return deviceRepository.save(d);
    }

    @Transactional
    public void deleteDevice(String id) {
        Device d = requireDevice(id);
        recipeRefGuard.assertDeviceUnreferenced(id);
        d.setDeleted(true);
        deviceRepository.save(d);
    }

    // ---------- helpers ----------

    public DeviceType requireType(String id) {
        DeviceType t = typeRepository.findById(id).orElse(null);
        if (t == null || Boolean.TRUE.equals(t.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备类型不存在: " + id);
        }
        return t;
    }

    public DeviceArea requireArea(String id) {
        DeviceArea a = areaRepository.findById(id).orElse(null);
        if (a == null || Boolean.TRUE.equals(a.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备区域不存在: " + id);
        }
        return a;
    }

    public Device requireDevice(String id) {
        Device d = deviceRepository.findById(id).orElse(null);
        if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + id);
        }
        return d;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** 配方侧引用校验的窄接口（避免 device → recipe 循环依赖）。 */
    public interface RecipeRefGuard {
        void assertDeviceUnreferenced(String deviceId);
    }
}
