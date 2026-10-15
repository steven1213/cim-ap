package com.cim.rms.server.integration;

import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceChamber;
import com.cim.rms.server.device.DeviceChamberRepository;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.device.DeviceTypeRepository;
import com.cim.rms.server.qualification.RecipeQualification;
import com.cim.rms.server.qualification.RecipeQualificationRepository;
import com.cim.rms.server.recipe.Recipe;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备视角：「这台机台上到底有哪些配方」。
 *
 * <p>这是行业最常用的作业入口（TSMC US6968245「机台主界面」/ AMAT 设备视图），
 * 回答的是<b>运维视角的问题</b>，与配方库（主数据视角）互补。</p>
 *
 * <p>数据来源四路合并，逐项标注 {@code sources}，可解释、可追溯：</p>
 * <ol>
 *   <li>{@code TYPE_APPLICABLE} 该机型下定义的配方（理论可用）；</li>
 *   <li>{@code PPID_MAPPED}    该机台已建立 PPID↔配方映射（配置在机台上）；</li>
 *   <li>{@code QUALIFIED}      该机台已登记配方资格（FR-Q1）；</li>
 *   <li>{@code ON_TOOL}        机台实存清单上报命中（S7F19 快照）。</li>
 * </ol>
 *
 * <p>额外输出两类异常行：<b>孤儿配方</b>（机台有、RMS 无映射 → {@code recipeId=null} + {@code onToolState=ORPHAN}）
 * 与<b>漂移</b>（映射存在但机台 HASH 不符 → {@code onToolState=DRIFT}）。</p>
 */
@Service
@RequiredArgsConstructor
public class DeviceRecipeViewService {

    public static final String SRC_TYPE = "TYPE_APPLICABLE";
    public static final String SRC_PPID = "PPID_MAPPED";
    public static final String SRC_QUAL = "QUALIFIED";
    public static final String SRC_ON_TOOL = "ON_TOOL";

    private final DeviceRepository deviceRepository;
    private final DeviceTypeRepository deviceTypeRepository;
    private final DeviceChamberRepository chamberRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final ToolRecipeMapRepository mapRepository;
    private final RecipeQualificationRepository qualificationRepository;
    private final ToolRecipeInventoryRepository inventoryRepository;

    /** 机台配方行。{@code recipeId} 为空表示「机台有、RMS 未纳管」的孤儿配方；
     *  {@code mapId} 供前端直接解绑（机台详情抽屉内操作，一处搞定）。 */
    public record DeviceRecipeRow(
            String recipeId, String code, String name,
            String deviceTypeId, String deviceTypeName,
            String activeVersionId, Integer activeVersionNo, String versionStatus, String bodyHash,
            List<String> sources,
            String mapId, String ppidOnTool, String chamberId, String chamberCode, String chamberName,
            String qualificationStatus, String onToolState) {
    }

    /** 设备视角汇总。 */
    public record DeviceRecipeView(String deviceId, String deviceCode, String deviceName,
                                  String deviceTypeId, String deviceTypeName, String areaId,
                                  int total, int orphan, int drift, int unqualified,
                                  List<DeviceRecipeRow> rows) {
    }

    public DeviceRecipeView view(String deviceId) {
        Device device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || Boolean.TRUE.equals(device.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + deviceId);
        }
        String typeName = deviceTypeRepository.findById(device.getDeviceTypeId())
                .map(t -> t.getName()).orElse(null);

        List<Recipe> applicable = recipeRepository.findByDeviceTypeIdAndDeletedFalse(device.getDeviceTypeId());
        List<ToolRecipeMap> maps = mapRepository.findByDeviceIdAndActiveTrueAndDeletedFalse(deviceId);
        List<String> deadMapPpids = new java.util.ArrayList<>();
        List<RecipeQualification> quals = qualificationRepository.findByDeviceIdAndDeletedFalse(deviceId);
        List<ToolRecipeInventory> inventory = inventoryRepository
                .findByDeviceIdAndDeletedFalseOrderByPpidOnToolAsc(deviceId);

        Map<String, String> qualByRecipe = new LinkedHashMap<>();
        for (RecipeQualification q : quals) {
            String prev = qualByRecipe.get(q.getRecipeId());
            // GRANTED 优先于 REVOKED（同一配方可能既有历史吊销又有现行授予）
            if (prev == null || (!"GRANTED".equals(prev) && "GRANTED".equals(q.getStatus()))) {
                qualByRecipe.put(q.getRecipeId(), q.getStatus());
            }
        }
        Map<String, String> invByPpid = new LinkedHashMap<>();
        for (ToolRecipeInventory t : inventory) {
            invByPpid.put(t.getPpidOnTool(), t.getReconcileResult() == null ? null : t.getReconcileResult().name());
        }

        // 合并三路「RMS 侧已知」的配方
        LinkedHashMap<String, Mutable> acc = new LinkedHashMap<>();
        for (Recipe r : applicable) {
            acc.computeIfAbsent(r.getId(), k -> new Mutable(r)).sources.add(SRC_TYPE);
        }
        // 同一配方多条 PPID 映射折叠为一行（行内 ppid 取首个映射，与 mapByRecipe 原意一致）
        for (ToolRecipeMap m : maps) {
            Recipe r = recipeRepository.findById(m.getRecipeId()).orElse(null);
            if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
                // 映射指向已删配方：机台仍装载着 RMS 已不存在的配方（绕过变更管控）→ 计孤儿，不静默吞掉
                deadMapPpids.add(m.getPpidOnTool());
                continue;
            }
            Mutable mu = acc.computeIfAbsent(r.getId(), k -> new Mutable(r));
            mu.addSource(SRC_PPID);
            if (mu.map == null) {
                mu.map = m;
            }
        }
        for (RecipeQualification q : quals) {
            Recipe r = recipeRepository.findById(q.getRecipeId()).orElse(null);
            if (r == null || Boolean.TRUE.equals(r.getDeleted())) {
                continue;
            }
            Mutable mu = acc.computeIfAbsent(r.getId(), k -> new Mutable(r));
            mu.addSource(SRC_QUAL);
        }

        List<DeviceRecipeRow> rows = new ArrayList<>();
        int orphan = 0;
        int drift = 0;
        int unqualified = 0;

        for (Mutable mu : acc.values()) {
            Recipe r = mu.recipe;
            RecipeVersion active = r.getActiveVersionId() == null ? null
                    : versionRepository.findById(r.getActiveVersionId()).orElse(null);
            String ppid = mu.map == null ? null : mu.map.getPpidOnTool();
            String onTool = ppid == null ? null : invByPpid.get(ppid);
            if (ppid != null && onTool != null) {
                mu.addSource(SRC_ON_TOOL);
            }
            if ("DRIFT".equals(onTool)) {
                drift++;
            }
            String qual = qualByRecipe.get(r.getId());
            if (!"GRANTED".equals(qual)) {
                unqualified++;
            }
            String chamberId = mu.map == null ? null : mu.map.getChamberId();
            DeviceChamber ch = chamberId == null ? null : chamberRepository.findById(chamberId).orElse(null);

            rows.add(new DeviceRecipeRow(r.getId(), r.getCode(), r.getName(),
                    r.getDeviceTypeId(), typeName,
                    active == null ? null : active.getId(),
                    active == null ? null : active.getVersionNo(),
                    active == null ? null : active.getStatus().name(),
                    active == null ? null : active.getBodyHash(),
                    List.copyOf(mu.sources), mu.map == null ? null : mu.map.getId(), ppid,
                    chamberId, ch == null ? null : ch.getCode(), ch == null ? null : ch.getName(),
                    qual, onTool));
        }

        // 孤儿：机台清单上报的 PPID 在 RMS 侧没有任何映射（未纳管 = 绕过变更管控）
        for (ToolRecipeInventory t : inventory) {
            boolean mapped = maps.stream().anyMatch(m -> m.getPpidOnTool().equals(t.getPpidOnTool()));
            if (!mapped) {
                orphan++;
                rows.add(new DeviceRecipeRow(null, null, null, device.getDeviceTypeId(), typeName,
                        null, null, null, null, List.of(SRC_ON_TOOL),
                        null, t.getPpidOnTool(), null, null, null, null, "ORPHAN"));
            }
        }
        // 孤儿补充：PPID 映射指向已删除的配方（RMS 侧曾纳管、现已删除；映射残留）
        for (String ppid : deadMapPpids) {
            orphan++;
            rows.add(new DeviceRecipeRow(null, null, null, device.getDeviceTypeId(), typeName,
                    null, null, null, null, List.of(SRC_ON_TOOL),
                    null, ppid, null, null, null, null, "ORPHAN"));
        }

        return new DeviceRecipeView(device.getId(), device.getCode(), device.getName(),
                device.getDeviceTypeId(), typeName, device.getAreaId(),
                rows.size(), orphan, drift, unqualified, rows);
    }

    /** 累积容器（合并多路来源）。 */
    private static final class Mutable {
        private final Recipe recipe;
        private final List<String> sources = new ArrayList<>();
        private ToolRecipeMap map;

        private Mutable(Recipe recipe) {
            this.recipe = recipe;
        }

        private Mutable addSource(String s) {
            if (!sources.contains(s)) {
                sources.add(s);
            }
            return this;
        }
    }
}
