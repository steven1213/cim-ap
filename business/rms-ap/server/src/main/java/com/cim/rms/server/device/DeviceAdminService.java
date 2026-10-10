package com.cim.rms.server.device;

import com.cim.rms.server.common.Wildcards;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public List<DeviceTypeSummary> listTypes() {
        return typeRepository.findByDeletedFalseOrderByCode().stream()
                .map(this::toTypeSummary).toList();
    }

    @Transactional
    public DeviceTypeSummary createType(String code, String name, String description,
                                       String manufacturer, String model) {
        if (typeRepository.findByCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "机型编码已存在: " + code);
        }
        DeviceType t = new DeviceType();
        t.setCode(code);
        t.setName(name);
        t.setDescription(description);
        t.setManufacturer(blankToNull(manufacturer));
        t.setModel(blankToNull(model));
        return toTypeSummary(typeRepository.save(t));
    }

    @Transactional
    public DeviceTypeSummary updateType(String id, String name, String description,
                                       String manufacturer, String model) {
        DeviceType t = requireType(id);
        if (name != null && !name.isBlank()) {
            t.setName(name);
        }
        if (description != null) {
            t.setDescription(description);
        }
        if (manufacturer != null) {
            t.setManufacturer(manufacturer.isBlank() ? null : manufacturer);
        }
        if (model != null) {
            t.setModel(model.isBlank() ? null : model);
        }
        return toTypeSummary(typeRepository.save(t));
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

    public List<DeviceAreaSummary> listAreas() {
        return areaRepository.findByDeletedFalseOrderBySortNoAscCode().stream()
                .map(this::toAreaSummary).toList();
    }

    @Transactional
    public DeviceAreaSummary createArea(String code, String name, String parentId, Integer sortNo, String description) {
        if (areaRepository.findByCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "区域编码已存在: " + code);
        }
        DeviceArea a = new DeviceArea();
        a.setCode(code);
        a.setName(name);
        a.setParentId(parentId == null || parentId.isBlank() ? null : requireArea(parentId).getId());
        a.setSortNo(sortNo == null ? 0 : sortNo);
        a.setDescription(description);
        return toAreaSummary(areaRepository.save(a));
    }

    @Transactional
    public DeviceAreaSummary updateArea(String id, String name, String parentId, Integer sortNo, String description) {
        DeviceArea a = requireArea(id);
        if (name != null && !name.isBlank()) {
            a.setName(name);
        }
        // 改父：防止自指成环（子树成环检测 W3+）
        if (parentId != null) {
            if (parentId.equals(a.getId())) {
                throw new BizException(BizCode.PARAM_INVALID, "区域父级不能是自身: " + a.getCode());
            }
            a.setParentId(parentId.isBlank() ? null : requireArea(parentId).getId());
        }
        if (sortNo != null) {
            a.setSortNo(sortNo);
        }
        if (description != null) {
            a.setDescription(description);
        }
        return toAreaSummary(areaRepository.save(a));
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

    public List<DeviceSummary> listDevices(String keyword, String deviceTypeId, String areaId, DeviceStatus status) {
        String like = Wildcards.toLikePattern(keyword);
        List<Device> devices = deviceRepository.search(like, blankToNull(deviceTypeId), blankToNull(areaId), status,
                Sort.by(Sort.Order.asc("code")));
        Map<String, String> typeNames = typeRepository.findByDeletedFalseOrderByCode().stream()
                .collect(java.util.stream.Collectors.toMap(DeviceType::getId, DeviceType::getName, (a, b) -> a));
        Map<String, String> areaNames = areaRepository.findByDeletedFalseOrderBySortNoAscCode().stream()
                .collect(java.util.stream.Collectors.toMap(DeviceArea::getId, DeviceArea::getName, (a, b) -> a));
        return devices.stream().map(d -> toDeviceSummary(d, typeNames, areaNames)).toList();
    }

    @Transactional
    public DeviceSummary createDevice(String code, String name, String deviceTypeId, String areaId,
                                     String ip, String description) {
        if (deviceRepository.findByCodeAndDeletedFalse(code).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "设备编码已存在: " + code);
        }
        Device d = new Device();
        d.setCode(code);
        d.setName(name);
        d.setDeviceTypeId(requireType(deviceTypeId).getId());
        d.setAreaId(areaId == null || areaId.isBlank() ? null : requireArea(areaId).getId());
        d.setIp(blankToNull(ip));
        d.setDescription(description);
        return toDeviceSummary(deviceRepository.save(d));
    }

    @Transactional
    public DeviceSummary updateDevice(String id, String name, String areaId, DeviceStatus status,
                                    String ip, String description) {
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
        if (ip != null) {
            d.setIp(ip.isBlank() ? null : ip);
        }
        if (description != null) {
            d.setDescription(description);
        }
        return toDeviceSummary(deviceRepository.save(d));
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

    // ---------- 摘要映射（前端契约稳定：派生联表名，备注走 description） ----------

    private DeviceTypeSummary toTypeSummary(DeviceType t) {
        return new DeviceTypeSummary(t.getId(), t.getCode(), t.getName(),
                t.getManufacturer(), t.getModel(), t.getDescription());
    }

    private DeviceAreaSummary toAreaSummary(DeviceArea a) {
        String parentName = a.getParentId() == null ? null
                : areaRepository.findById(a.getParentId()).map(DeviceArea::getName).orElse(null);
        return new DeviceAreaSummary(a.getId(), a.getCode(), a.getName(),
                a.getParentId(), parentName, a.getDescription());
    }

    private DeviceSummary toDeviceSummary(Device d) {
        Map<String, String> typeNames = new HashMap<>();
        if (d.getDeviceTypeId() != null) {
            typeRepository.findById(d.getDeviceTypeId()).ifPresent(t -> typeNames.put(t.getId(), t.getName()));
        }
        Map<String, String> areaNames = new HashMap<>();
        if (d.getAreaId() != null) {
            areaRepository.findById(d.getAreaId()).ifPresent(a -> areaNames.put(a.getId(), a.getName()));
        }
        return toDeviceSummary(d, typeNames, areaNames);
    }

    private DeviceSummary toDeviceSummary(Device d, Map<String, String> typeNames, Map<String, String> areaNames) {
        return new DeviceSummary(d.getId(), d.getCode(), d.getName(),
                d.getDeviceTypeId(), typeNames.get(d.getDeviceTypeId()),
                d.getAreaId(), areaNames.get(d.getAreaId()),
                d.getIp(), d.getStatus() == null ? DeviceStatus.ENABLED : d.getStatus(),
                d.getDescription());
    }

    /** 设备类型摘要（含制造商/型号；备注走 description）。 */
    public record DeviceTypeSummary(String id, String code, String name,
                                   String manufacturer, String model, String remark) {
    }

    /** 设备区域摘要（含父区域名）。 */
    public record DeviceAreaSummary(String id, String code, String name,
                                   String parentId, String parentName, String remark) {
    }

    /** 设备台账摘要（含机型名/区域名/IP/状态）。 */
    public record DeviceSummary(String id, String code, String name,
                               String deviceTypeId, String deviceTypeName,
                               String areaId, String areaName, String ip,
                               DeviceStatus status, String remark) {
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** 配方侧引用校验的窄接口（避免 device → recipe 循环依赖）。 */
    public interface RecipeRefGuard {
        void assertDeviceUnreferenced(String deviceId);
    }
}
