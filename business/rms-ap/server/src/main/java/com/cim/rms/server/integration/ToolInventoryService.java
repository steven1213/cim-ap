package com.cim.rms.server.integration;

import com.cim.rms.server.audit.RmsAuditService;
import com.cim.rms.server.device.Device;
import com.cim.rms.server.device.DeviceRepository;
import com.cim.rms.server.recipe.Recipe;
import com.cim.rms.server.recipe.RecipeRepository;
import com.cim.rms.server.recipe.RecipeVersion;
import com.cim.rms.server.recipe.RecipeVersionRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 机台配方清单服务（S7F19「请求机台可用配方目录」的落库与核对）。
 *
 * <p><b>行业习惯</b>：成熟的 RMS 不只管「我下发过什么」，还必须知道「机台上现在到底有什么」。
 * 没有这张清单，就回答不了三个基本问题——机台上有没有仓库不知道的配方（未纳管/孤儿）、
 * 机台上的配方还是不是仓库那一份（漂移）、仓库以为机台有的配方是不是真在（缺失）。</p>
 *
 * <p>核对三类结论（{@link ReconcileResult}）：</p>
 * <ul>
 *   <li>{@code ORPHAN} —— 机台有、RMS 无映射：<b>未纳管配方</b>，合规/安全风险最高的一类；</li>
 *   <li>{@code DRIFT} —— 映射存在但机台 HASH ≠ 生效版 HASH：机台侧被改过；</li>
 *   <li>{@code OK} —— 映射存在且 HASH 一致。</li>
 * </ul>
 *
 * <p>另有 {@code MISSING}（RMS 有映射、机台未上报）在报告里以 PPID 列表给出——它意味着
 * 「按资格矩阵该机台应能跑某配方，机台上却没有」，同样会导致 Run 货失败，需运维核对。</p>
 *
 * <p>报警克制：只有<b>新出现</b>（与上一轮快照相比由 OK 变 ORPHAN/DRIFT）才报警，避免每轮巡检都刷一遍
 * 同一个老问题，把真正的新问题淹没。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolInventoryService {

    private final ToolRecipeInventoryRepository inventoryRepository;
    private final ToolRecipeMapRepository mapRepository;
    private final DeviceRepository deviceRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeVersionRepository versionRepository;
    private final ToolRecipeMapService toolRecipeMapService;
    private final IntegrationService integration;
    private final RmsAuditService audit;

    /**
     * 受理一次机台配方目录上报（EAP 从 S7F19/F20 取回后上报）。
     *
     * <p>幂等语义为「快照刷新」：同一 (机台, PPID) 覆盖为最新一次，重复上报不会产生重复行。</p>
     */
    @Transactional
    public InventoryReport report(InventoryReportReq req) {
        Device device = resolveDevice(req.toolId());
        Instant seen = req.seenAt() == null ? Instant.now() : req.seenAt();
        List<InventoryItem> items = req.items() == null ? List.of() : req.items();

        int ok = 0, drift = 0, orphan = 0, newlyDetected = 0;
        Set<String> reportedPpids = new LinkedHashSet<>();
        for (InventoryItem it : items) {
            if (it == null || it.ppid() == null || it.ppid().isBlank()) {
                continue;
            }
            String ppid = it.ppid().trim();
            reportedPpids.add(ppid);
            ToolRecipeInventory row = inventoryRepository
                    .findFirstByDeviceIdAndPpidOnToolAndDeletedFalse(device.getId(), ppid)
                    .orElseGet(ToolRecipeInventory::new);
            ReconcileResult before = row.getId() == null ? null : row.getReconcileResult();

            Recipe mapped = toolRecipeMapService.resolve(device.getId(), device.getDeviceTypeId(), ppid);
            String expected = mapped == null ? null : activeHash(mapped);
            ReconcileResult result = reconcile(mapped != null, it.bodyHash(), expected);

            row.setDeviceId(device.getId());
            row.setPpidOnTool(ppid);
            row.setToolBodyHash(blankToNull(it.bodyHash()));
            row.setMappedRecipeId(mapped == null ? null : mapped.getId());
            row.setExpectedHash(expected);
            row.setReconcileResult(result);
            row.setLastSeenAt(seen);
            inventoryRepository.save(row);

            switch (result) {
                case ORPHAN -> orphan++;
                case DRIFT -> drift++;
                default -> ok++;
            }
            // 只在「新出现」时报警：避免每轮巡检重复报同一老问题
            if (before != result && (result == ReconcileResult.ORPHAN || result == ReconcileResult.DRIFT)) {
                newlyDetected++;
                if (result == ReconcileResult.ORPHAN) {
                    integration.raiseAlarm("RMS_ORPHAN_PPID", "MINOR", device.getId(), null, ppid,
                            "机台配方目录核对发现未纳管配方：PPID=" + ppid + "（机台有、RMS 无映射）",
                            req.correlationId());
                } else {
                    integration.raiseAlarm("RMS_DRIFT", "MAJOR", device.getId(), null, ppid,
                            "机台配方目录核对发现漂移：PPID=" + ppid + "（机台 HASH 与生效版不一致）",
                            req.correlationId());
                }
            }
        }

        // MISSING：RMS 有映射、机台未上报
        List<String> missing = mapRepository.findByDeviceIdAndDeletedFalse(device.getId()).stream()
                .filter(ToolRecipeMap::isActive)
                .map(ToolRecipeMap::getPpidOnTool)
                .filter(p -> !reportedPpids.contains(p))
                .sorted()
                .toList();

        audit.success("TOOL_INVENTORY_REPORT", device.getCode(),
                "机台配方目录：共 " + reportedPpids.size() + " · OK " + ok + " · 漂移 " + drift
                        + " · 孤儿 " + orphan + " · 缺失 " + missing.size());
        return new InventoryReport(device.getId(), device.getCode(), reportedPpids.size(), ok, drift,
                orphan, newlyDetected, missing, fmt(seen));
    }

    /** 清单查询（可按机台/结论过滤）。 */
    @Transactional(readOnly = true)
    public List<InventoryView> list(String deviceId, String result) {
        ReconcileResult r = parseResultOrNull(result);
        List<ToolRecipeInventory> rows;
        if (deviceId != null && !deviceId.isBlank()) {
            rows = inventoryRepository.findByDeviceIdAndDeletedFalseOrderByPpidOnToolAsc(deviceId);
        } else if (r != null) {
            rows = inventoryRepository.findByReconcileResultAndDeletedFalseOrderByCreateTimeDesc(r);
        } else {
            rows = inventoryRepository.findByDeletedFalseOrderByCreateTimeDesc();
        }
        return rows.stream()
                .filter(x -> r == null || x.getReconcileResult() == r)
                .map(this::toView)
                .toList();
    }

    /** 清单汇总（按机台或全局）。 */
    @Transactional(readOnly = true)
    public InventorySummary summary(String deviceId) {
        List<ToolRecipeInventory> rows = (deviceId == null || deviceId.isBlank())
                ? inventoryRepository.findByDeletedFalseOrderByCreateTimeDesc()
                : inventoryRepository.findByDeviceIdAndDeletedFalseOrderByPpidOnToolAsc(deviceId);
        long ok = rows.stream().filter(x -> x.getReconcileResult() == ReconcileResult.OK).count();
        long drift = rows.stream().filter(x -> x.getReconcileResult() == ReconcileResult.DRIFT).count();
        long orphan = rows.stream().filter(x -> x.getReconcileResult() == ReconcileResult.ORPHAN).count();
        return new InventorySummary(deviceId, rows.size(), (int) ok, (int) drift, (int) orphan);
    }

    // ==================== helpers ====================

    private static ReconcileResult reconcile(boolean mapped, String toolHash, String expectedHash) {
        if (!mapped) {
            return ReconcileResult.ORPHAN;
        }
        if (toolHash != null && !toolHash.isBlank() && expectedHash != null && !expectedHash.isBlank()
                && !toolHash.equalsIgnoreCase(expectedHash)) {
            return ReconcileResult.DRIFT;
        }
        return ReconcileResult.OK;
    }

    private String activeHash(Recipe recipe) {
        if (recipe.getActiveVersionId() == null) {
            return null;
        }
        return versionRepository.findById(recipe.getActiveVersionId())
                .filter(v -> v.getStatus() == com.cim.rms.server.recipe.RecipeVersionStatus.ACTIVE)
                .map(RecipeVersion::getBodyHash).orElse(null);
    }

    private Device resolveDevice(String toolId) {
        if (toolId == null || toolId.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "toolId 不能为空");
        }
        Device d = deviceRepository.findById(toolId).orElse(null);
        if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
            d = deviceRepository.findByCodeAndDeletedFalse(toolId).orElse(null);
        }
        if (d == null || Boolean.TRUE.equals(d.getDeleted())) {
            throw new BizException(BizCode.DATA_NOT_FOUND, "设备不存在: " + toolId);
        }
        return d;
    }

    private static ReconcileResult parseResultOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ReconcileResult.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(BizCode.PARAM_INVALID,
                    "未知 reconcileResult: " + raw + "（可选 OK / DRIFT / ORPHAN）");
        }
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

    private InventoryView toView(ToolRecipeInventory x) {
        String deviceCode = deviceRepository.findById(x.getDeviceId()).map(Device::getCode).orElse(null);
        String recipeCode = x.getMappedRecipeId() == null ? null
                : recipeRepository.findById(x.getMappedRecipeId())
                        .map(Recipe::getCode).orElse(null);
        return new InventoryView(x.getId(), x.getDeviceId(), deviceCode, x.getPpidOnTool(),
                x.getToolBodyHash(), x.getMappedRecipeId(), recipeCode, x.getExpectedHash(),
                x.getReconcileResult().name(), fmt(x.getLastSeenAt()));
    }

    // ==================== 输入 / 视图 ====================

    /** 机台配方目录上报请求。 */
    public record InventoryReportReq(String toolId, String correlationId, Instant seenAt,
                                     List<InventoryItem> items) {
    }

    /** 目录中单个配方（PPID + 机台回读 HASH，HASH 可缺省——缺省时只能判孤儿，判不了漂移）。 */
    public record InventoryItem(String ppid, String bodyHash) {
    }

    /** 核对报告。 */
    public record InventoryReport(String toolId, String deviceCode, int total, int ok, int drift,
                                  int orphan, int newlyDetected, List<String> missing, String reportedAt) {
    }

    /** 清单行视图。 */
    public record InventoryView(String id, String deviceId, String deviceCode, String ppidOnTool,
                                String toolBodyHash, String mappedRecipeId, String recipeCode,
                                String expectedHash, String reconcileResult, String lastSeenAt) {
    }

    /** 清单汇总。 */
    public record InventorySummary(String deviceId, int total, int ok, int drift, int orphan) {
    }
}
