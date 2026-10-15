package com.cim.rms.server.integration;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.device.DeviceTypeRepository;
import com.cim.rms.server.recipe.Recipe;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 机台 PPID ↔ 配方映射服务（D7 / IF-E2）。
 *
 * <p>EAP 上传/下载/在线比对只携带机台侧 PPID 时，由本服务解析到 RMS 配方；
 * 解析优先级：<b>机台级映射 &gt; 机型级映射</b>。未命中时不臆造，交由调用方兜底。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolRecipeMapService {

    private final ToolRecipeMapRepository repository;
    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final com.cim.rms.server.device.DeviceChamberRepository chamberRepository;
    private final RecipeRepository recipeRepository;
    private final com.cim.rms.server.qualification.RecipeQualificationRepository qualificationRepository;
    private final RmsAuditService audit;

    public List<MapView> list(String deviceId) {
        List<ToolRecipeMap> rows = (deviceId == null || deviceId.isBlank())
                ? repository.findByDeletedFalseOrderByCreateTimeDesc()
                : repository.findAll().stream()
                        .filter(m -> !Boolean.TRUE.equals(m.getDeleted()) && deviceId.equals(m.getDeviceId()))
                        .toList();
        return rows.stream().map(this::toView).toList();
    }

    /** 建立/更新映射（同维度同 PPID 唯一）。{@code chamberId} 为空表示机台级映射。 */
    @Transactional
    public MapView register(String deviceId, String deviceTypeId, String ppidOnTool, String recipeId,
                            String chamberId) {
        if (ppidOnTool == null || ppidOnTool.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "ppidOnTool 不能为空");
        }
        Recipe r = recipeRepository.findById(recipeId).orElse(null);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            r = recipeRepository.findByCodeAndDeletedFalse(recipeId).orElse(null);
        }
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + recipeId);
        }
        String devId = blankToNull(deviceId);
        String typeId = blankToNull(deviceTypeId);
        if (devId == null && typeId == null) {
            throw new BizException(BizCode.PARAM_INVALID, "deviceId 与 deviceTypeId 至少指定一项");
        }
        if (devId != null) {
            deviceRepository.findById(devId).orElseThrow(
                    () -> new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + deviceId));
        }
        if (typeId != null) {
            deviceTypeRepository.findById(typeId).orElseThrow(
                    () -> new BizException(BizCode.DATA_NOT_FOUND, "机型不存在: " + deviceTypeId));
        }
        // 腔体：必须存在且归属该机台（机型级映射不允许绑腔体——腔体属于具体机台）
        String chamber = blankToNull(chamberId);
        if (chamber != null) {
            if (devId == null) {
                throw new BizException(BizCode.PARAM_INVALID, "机型级映射不能绑定腔体（腔体属于具体机台）");
            }
            var c = chamberRepository.findById(chamber).orElse(null);
            if (c == null || Boolean.TRUE.equals(c.getDeleted())) {
                throw new BizException(BizCode.DATA_NOT_FOUND, "腔体不存在: " + chamberId);
            }
            if (!devId.equals(c.getDeviceId())) {
                throw new BizException(BizCode.PARAM_INVALID, "腔体不属于该机台: " + chamberId);
            }
        }
        String ppid = ppidOnTool.trim();
        ToolRecipeMap m = (devId != null
                ? repository.findFirstByDeviceIdAndPpidOnToolAndDeletedFalse(devId, ppid)
                : repository.findFirstByDeviceTypeIdAndPpidOnToolAndDeletedFalse(typeId, ppid))
                .orElseGet(ToolRecipeMap::new);
        boolean created = m.getId() == null;
        m.setDeviceId(devId);
        m.setDeviceTypeId(devId != null ? null : typeId);
        m.setPpidOnTool(ppid);
        m.setChamberId(chamber);
        m.setRecipeId(r.getId());
        m.setActive(true);
        ToolRecipeMap saved = repository.save(m);
        audit.success(created ? "PPID_MAP_CREATED" : "PPID_MAP_UPDATED", r.getCode(),
                "PPID " + ppid + " → " + r.getCode() + "（" + (devId != null ? "机台级" : "机型级")
                        + (chamber == null ? "" : " · 腔体") + "）");
        return toView(saved);
    }

    /** 兼容旧签名（机台级映射，不带腔体）。 */
    @Transactional
    public MapView register(String deviceId, String deviceTypeId, String ppidOnTool, String recipeId) {
        return register(deviceId, deviceTypeId, ppidOnTool, recipeId, null);
    }

    @Transactional
    public void delete(String id) {
        ToolRecipeMap m = require(id);
        m.setDeleted(true);
        repository.save(m);
        audit.success("PPID_MAP_DELETED", null, "删除 PPID 映射 " + m.getPpidOnTool());
    }

    /**
     * 解析机台 PPID 对应的 RMS 配方：机台级优先，其次机型级；未命中返回 {@code null}。
     */
    public Recipe resolve(String deviceId, String deviceTypeId, String ppidOnTool) {
        if (ppidOnTool == null || ppidOnTool.isBlank()) {
            return null;
        }
        String ppid = ppidOnTool.trim();
        if (deviceId != null && !deviceId.isBlank()) {
            Recipe r = repository.findFirstByDeviceIdAndPpidOnToolAndActiveTrueAndDeletedFalse(deviceId, ppid)
                    .flatMap(m -> recipeRepository.findById(m.getRecipeId())).orElse(null);
            if (r != null && !Boolean.TRUE.equals(r.getDeleted())) {
                return r;
            }
        }
        String typeId = deviceTypeId;
        if (typeId == null && deviceId != null && !deviceId.isBlank()) {
            typeId = deviceRepository.findById(deviceId).map(Device::getDeviceTypeId).orElse(null);
        }
        if (typeId != null && !typeId.isBlank()) {
            Recipe r = repository.findFirstByDeviceTypeIdAndPpidOnToolAndActiveTrueAndDeletedFalse(typeId, ppid)
                    .flatMap(m -> recipeRepository.findById(m.getRecipeId())).orElse(null);
            if (r != null && !Boolean.TRUE.equals(r.getDeleted())) {
                return r;
            }
        }
        return null;
    }

    private ToolRecipeMap require(String id) {
        ToolRecipeMap m = repository.findById(id).orElse(null);
        if (m == null || Boolean.TRUE.equals(m.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "PPID 映射不存在: " + id);
        }
        return m;
    }

    /**
     * 配方视角反查（W7 体验整合）：该配方被装载到哪些机台（含腔体、PPID 与资格状态）。
     *
     * <p>回答用户动线问题「这个配方用在哪些机台上」——不再要求用户跳到集成监控页交叉比对。</p>
     */
    public List<ToolUsage> usagesOf(String recipeId) {
        Recipe r = recipeRepository.findById(recipeId).orElse(null);
        if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "配方不存在: " + recipeId);
        }
        // 资格按设备聚合（GRANTED 优先于 REVOKED——与 DeviceRecipeViewService 同口径）
        java.util.Map<String, String> qualByDevice = new java.util.LinkedHashMap<>();
        for (com.cim.rms.server.qualification.RecipeQualification q
                : qualificationRepository.findByRecipeIdAndDeletedFalse(recipeId)) {
            if (q.getDeviceId() == null) {
                continue; // 机型级资格不落在单机台行上
            }
            String prev = qualByDevice.get(q.getDeviceId());
            if (prev == null || (!"GRANTED".equals(prev) && "GRANTED".equals(q.getStatus()))) {
                qualByDevice.put(q.getDeviceId(), q.getStatus());
            }
        }
        return repository.findByRecipeIdAndDeletedFalse(recipeId).stream().map(m -> {
            Device d = m.getDeviceId() == null ? null
                    : deviceRepository.findById(m.getDeviceId()).orElse(null);
            var c = m.getChamberId() == null ? null
                    : chamberRepository.findById(m.getChamberId()).orElse(null);
            return new ToolUsage(m.getId(), m.getDeviceId(),
                    d == null ? null : d.getCode(), d == null ? null : d.getName(),
                    m.getPpidOnTool(), m.getChamberId(), c == null ? null : c.getCode(),
                    m.getDeviceId() == null ? null : qualByDevice.get(m.getDeviceId()),
                    m.isActive());
        }).toList();
    }

    /** 配方装载视图（配方详情抽屉「装载机台」区块行）。 */
    public record ToolUsage(String id, String deviceId, String deviceCode, String deviceName,
                            String ppidOnTool, String chamberId, String chamberCode,
                            String qualificationStatus, boolean active) {
    }

    private MapView toView(ToolRecipeMap m) {        String deviceCode = m.getDeviceId() == null ? null
                : deviceRepository.findById(m.getDeviceId()).map(Device::getCode).orElse(null);
        String typeName = m.getDeviceTypeId() == null ? null
                : deviceTypeRepository.findById(m.getDeviceTypeId()).map(t -> t.getName()).orElse(null);
        String recipeCode = recipeRepository.findById(m.getRecipeId()).map(Recipe::getCode).orElse(null);
        String chamberCode = m.getChamberId() == null ? null
                : chamberRepository.findById(m.getChamberId()).map(c -> c.getCode()).orElse(null);
        return new MapView(m.getId(), m.getDeviceId(), deviceCode, m.getDeviceTypeId(), typeName,
                m.getPpidOnTool(), m.getChamberId(), chamberCode, m.getRecipeId(), recipeCode,
                m.isActive(), fmt(m.getCreateTime()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String fmt(Instant t) {
        return t == null ? null : LocalDateTime.ofInstant(t, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 映射视图。{@code chamberId}/{@code chamberCode} 为空表示机台级映射。 */
    public record MapView(String id, String deviceId, String deviceCode, String deviceTypeId,
                          String deviceTypeName, String ppidOnTool, String chamberId, String chamberCode,
                          String recipeId, String recipeCode, boolean active, String createTime) {
    }
}
