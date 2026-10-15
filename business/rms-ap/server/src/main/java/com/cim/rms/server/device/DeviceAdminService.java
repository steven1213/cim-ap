package com.cim.rms.server.device;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.common.Wildcards;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final DeviceChamberRepository chamberRepository;
    private final RecipeRefGuard recipeRefGuard;
    private final ChamberRefGuard chamberRefGuard;
    private final RmsAuditService audit;

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
        DeviceTypeSummary summary = toTypeSummary(typeRepository.save(t));
        audit.success("DEVICE_TYPE_CREATED", t.getCode(), t.getName());
        return summary;
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
        DeviceTypeSummary summary = toTypeSummary(typeRepository.save(t));
        audit.success("DEVICE_TYPE_UPDATED", t.getCode(), t.getName());
        return summary;
    }

    @Transactional
    public void deleteType(String id) {
        DeviceType t = requireType(id);
        if (!deviceRepository.findByDeviceTypeIdAndDeletedFalse(id).isEmpty()) {
            throw new BizException(BizCode.PARAM_INVALID, "机型仍被设备引用，禁止删除: " + t.getCode());
        }
        t.setDeleted(true);
        typeRepository.save(t);
        audit.success("DEVICE_TYPE_DELETED", t.getCode(), t.getName());
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
        DeviceAreaSummary summary = toAreaSummary(areaRepository.save(a));
        audit.success("DEVICE_AREA_CREATED", a.getCode(), a.getName());
        return summary;
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
        DeviceAreaSummary summary = toAreaSummary(areaRepository.save(a));
        audit.success("DEVICE_AREA_UPDATED", a.getCode(), a.getName());
        return summary;
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
        audit.success("DEVICE_AREA_DELETED", a.getCode(), a.getName());
    }

    // ---------- 机台腔体（Chamber；W7） ----------

    public List<ChamberSummary> listChambers(String deviceId) {
        List<DeviceChamber> list = (deviceId == null || deviceId.isBlank())
                ? chamberRepository.findByDeletedFalseOrderByCreateTimeDesc()
                : chamberRepository.findByDeviceIdAndDeletedFalseOrderBySortNoAscCode(deviceId);
        return list.stream().map(this::toChamberSummary).toList();
    }

    @Transactional
    public ChamberSummary createChamber(String deviceId, String code, String name, Integer sortNo, String description) {
        Device d = requireDevice(deviceId);
        if (chamberRepository.findByDeviceIdAndCodeAndDeletedFalse(deviceId, code).isPresent()) {
            throw new BizException(BizCode.PARAM_INVALID, "该机台腔体编码已存在: " + code);
        }
        DeviceChamber c = new DeviceChamber();
        c.setDeviceId(d.getId());
        c.setCode(code);
        c.setName(name);
        c.setSortNo(sortNo == null ? 0 : sortNo);
        c.setDescription(description);
        ChamberSummary summary = toChamberSummary(chamberRepository.save(c));
        audit.success("DEVICE_CHAMBER_CREATED", d.getCode() + "/" + code, name);
        return summary;
    }

    @Transactional
    public ChamberSummary updateChamber(String id, String name, Integer sortNo, String description) {
        DeviceChamber c = requireChamber(id);
        if (name != null && !name.isBlank()) {
            c.setName(name);
        }
        if (sortNo != null) {
            c.setSortNo(sortNo);
        }
        if (description != null) {
            c.setDescription(description);
        }
        ChamberSummary summary = toChamberSummary(chamberRepository.save(c));
        audit.success("DEVICE_CHAMBER_UPDATED", c.getCode(), c.getName());
        return summary;
    }

    @Transactional
    public void deleteChamber(String id) {
        DeviceChamber c = requireChamber(id);
        // 引用保护：腔体被 PPID 映射引用时禁止删除（否则映射会指向空气）
        chamberRefGuard.assertChamberUnreferenced(id);
        c.setDeleted(true);
        chamberRepository.save(c);
        audit.success("DEVICE_CHAMBER_DELETED", c.getCode(), c.getName());
    }

    public DeviceChamber requireChamber(String id) {
        DeviceChamber c = chamberRepository.findById(id).orElse(null);
        if (c == null || Boolean.TRUE.equals(c.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "腔体不存在: " + id);
        }
        return c;
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
        if (areaId != null && !areaId.isBlank()) {
            DeviceArea area = requireArea(areaId);
            assertAreaUnderFactory(area);
            d.setAreaId(area.getId());
        } else {
            d.setAreaId(null);
        }
        d.setIp(blankToNull(ip));
        d.setDescription(description);
        DeviceSummary summary = toDeviceSummary(deviceRepository.save(d));
        audit.success("DEVICE_CREATED", d.getCode(), d.getName());
        return summary;
    }

    @Transactional
    public DeviceSummary updateDevice(String id, String name, String areaId, DeviceStatus status,
                                    String ip, String description) {
        Device d = requireDevice(id);
        if (name != null && !name.isBlank()) {
            d.setName(name);
        }
        if (areaId != null) {
            if (areaId.isBlank()) {
                d.setAreaId(null);
            } else {
                DeviceArea area = requireArea(areaId);
                assertAreaUnderFactory(area);
                d.setAreaId(area.getId());
            }
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
        DeviceSummary summary = toDeviceSummary(deviceRepository.save(d));
        audit.success("DEVICE_UPDATED", d.getCode(), d.getName());
        return summary;
    }

    @Transactional
    public void deleteDevice(String id) {
        Device d = requireDevice(id);
        recipeRefGuard.assertDeviceUnreferenced(id);
        d.setDeleted(true);
        deviceRepository.save(d);
        audit.success("DEVICE_DELETED", d.getCode(), d.getName());
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

    /**
     * 设备必须挂在「工厂下的区域」：所选区域不得是顶层工厂节点，且向上必须能找到工厂（root）。
     *
     * <p>保证「工厂 → 区域 → 机台」动线成立——直接挂工厂、或挂在没有工厂祖先的孤立区域上都会被拒。</p>
     */
    private void assertAreaUnderFactory(DeviceArea chosen) {
        if (chosen.getParentId() == null || chosen.getParentId().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "设备不可直接挂在工厂节点下，请选择工厂下的区域: " + chosen.getName());
        }
        Map<String, DeviceArea> byId = areaRepository.findByDeletedFalseOrderBySortNoAscCode().stream()
                .collect(Collectors.toMap(DeviceArea::getId, a -> a, (a, b) -> a));
        Set<String> visited = new HashSet<>();
        visited.add(chosen.getId());
        DeviceArea cur = byId.get(chosen.getParentId());
        while (cur != null) {
            if (!visited.add(cur.getId())) {
                throw new BizException(BizCode.PARAM_INVALID, "区域层级存在环，无法定位工厂: " + chosen.getName());
            }
            if (cur.getParentId() == null || cur.getParentId().isBlank()) {
                return; // 抵达工厂根
            }
            cur = byId.get(cur.getParentId());
        }
        throw new BizException(BizCode.PARAM_INVALID,
                "所选区域没有工厂祖先，请将其挂到工厂下: " + chosen.getName());
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

    /** 机台腔体摘要（含所属机台编码/名称）。 */
    public record ChamberSummary(String id, String deviceId, String deviceCode, String deviceName,
                                String code, String name, int sortNo, String remark) {
    }

    /** 区域树节点（嵌套 children，供设备视角左侧导航直接渲染）。 */
    public record AreaNode(String id, String code, String name, String parentId, int sortNo,
                           List<AreaNode> children) {
    }

    private ChamberSummary toChamberSummary(DeviceChamber c) {
        String deviceCode = deviceRepository.findById(c.getDeviceId()).map(Device::getCode).orElse(null);
        String deviceName = deviceRepository.findById(c.getDeviceId()).map(Device::getName).orElse(null);
        return new ChamberSummary(c.getId(), c.getDeviceId(), deviceCode, deviceName,
                c.getCode(), c.getName(), c.getSortNo(), c.getDescription());
    }

    /**
     * 区域树（嵌套结构）。区域自身无序（order by sortNo, code），父子关系由 {@code parentId} 决定；
     * 悬空父引用（父已被删）的节点按顶层处理，避免整棵子树丢节点。
     */
    public List<AreaNode> areaTree() {
        List<DeviceArea> all = areaRepository.findByDeletedFalseOrderBySortNoAscCode();
        Map<String, DeviceArea> byId = new HashMap<>();
        all.forEach(a -> byId.put(a.getId(), a));
        Map<String, List<DeviceArea>> children = new HashMap<>();
        List<DeviceArea> roots = new java.util.ArrayList<>();
        for (DeviceArea a : all) {
            String pid = a.getParentId();
            boolean isRoot = pid == null || pid.isBlank() || !byId.containsKey(pid);
            if (isRoot) {
                roots.add(a);
            } else {
                children.computeIfAbsent(pid, k -> new java.util.ArrayList<>()).add(a);
            }
        }
        return roots.stream().map(r -> toAreaNode(r, children)).toList();
    }

    private AreaNode toAreaNode(DeviceArea a, Map<String, List<DeviceArea>> children) {
        List<AreaNode> kids = children.getOrDefault(a.getId(), List.of()).stream()
                .map(c -> toAreaNode(c, children)).toList();
        return new AreaNode(a.getId(), a.getCode(), a.getName(), a.getParentId(), a.getSortNo(), kids);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** 配方侧引用校验的窄接口（避免 device → recipe 循环依赖）。 */
    public interface RecipeRefGuard {
        void assertDeviceUnreferenced(String deviceId);
    }

    /** 腔体引用校验的窄接口（由 integration 侧实现：PPID 映射引用腔体时禁止删腔体）。 */
    public interface ChamberRefGuard {
        void assertChamberUnreferenced(String chamberId);
    }
}
