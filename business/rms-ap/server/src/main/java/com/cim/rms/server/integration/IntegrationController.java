package com.cim.rms.server.integration;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

/**
 * 系统间接口 API（IF-E1/E2/E3/M1/A1）。路径前缀 {@code /api/v1/rms/integration}。
 *
 * <p>供 EAP/MES 联调与后续接入消息通道；同步查询/上报类走 REST，事件类将来走 cim-mq-starter。</p>
 */
@RestController
@RequestMapping("/api/v1/rms/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService service;
    private final ToolRecipeMapService ppidMapService;
    private final RecipeDownloadService downloadService;
    private final IntegrationIdempotencyService idempotencyService;
    private final EquipmentEventService eventService;
    private final ToolInventoryService inventoryService;

    /** IF-E1 配方上传（EAP → RMS）。 */
    @PostMapping("/upload")
    public Result<IntegrationService.UploadResult> upload(@RequestBody IntegrationService.UploadReq req) {
        return Result.ok(service.uploadRecipe(req));
    }

    /** IF-E2 配方下载（RMS → EAP → 设备）。 */
    @PostMapping("/download")
    public Result<IntegrationService.DownloadResult> download(@RequestBody IntegrationService.DownloadReq req) {
        return Result.ok(service.downloadRecipe(req));
    }

    /** IF-E3 在线比对（EAP → RMS）。 */
    @PostMapping("/online-compare")
    public Result<IntegrationService.OnlineCompareResult> onlineCompare(
            @RequestBody IntegrationService.OnlineCompareReq req) {
        return Result.ok(service.onlineCompare(req));
    }

    /** IF-M1 Run 货配方校验（MES → RMS）。 */
    @PostMapping("/run-check")
    public Result<IntegrationService.RunCheckResult> runCheck(@RequestBody IntegrationService.RunCheckReq req) {
        return Result.ok(service.runCheck(req));
    }

    // ---------- Hold ----------

    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/holds")
    public Result<List<IntegrationService.HoldView>> holds(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return Result.ok(service.listHolds(activeOnly));
    }

    @PreAuthorize("hasAuthority('rms:integration:operate')")
    @PostMapping("/holds/{id}/release")
    public Result<IntegrationService.HoldView> releaseHold(@PathVariable String id,
                                                           @RequestBody(required = false) ReleaseReq req) {
        return Result.ok(service.releaseHold(id, req == null ? null : req.reason()));
    }

    /** 批量 Hold / 解除（W9）：按设备/机型/配方展开，逐条独立事务部分成功。 */
    @PreAuthorize("hasAuthority('rms:integration:operate')")
    @PostMapping("/holds/batch")
    public Result<IntegrationService.BatchOpResult> batchHold(@RequestBody IntegrationService.BatchHoldReq req) {
        return Result.ok(service.batchHold(req));
    }

    /** 批量下发（W9）：逐条继承幂等/阶段机/资格闸门，共用 batchId 供进度聚合。 */
    @PreAuthorize("hasAuthority('rms:integration:operate')")
    @PostMapping("/downloads/batch")
    public Result<IntegrationService.BatchOpResult> batchDownload(
            @RequestBody IntegrationService.BatchDownloadReq req) {
        return Result.ok(service.batchDownload(req));
    }

    // ---------- Alarm ----------

    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/alarms")
    public Result<List<IntegrationService.AlarmView>> alarms(@RequestParam(defaultValue = "false") boolean unackOnly) {
        return Result.ok(service.listAlarms(unackOnly));
    }

    @PreAuthorize("hasAuthority('rms:integration:operate')")
    @PostMapping("/alarms/{id}/ack")
    public Result<IntegrationService.AlarmView> ack(@PathVariable String id) {
        return Result.ok(service.acknowledgeAlarm(id));
    }

    /** Alarm 代码字典（IF-A1 集中登记）。 */
    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/alarm-codes")
    public Result<Map<String, String>> alarmCodes() {
        return Result.ok(IntegrationService.ALARM_CODES);
    }

    // ---------- 机台 PPID ↔ 配方映射（D7 / IF-E2） ----------

    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/ppid-maps")
    public Result<List<ToolRecipeMapService.MapView>> ppidMaps(@RequestParam(required = false) String deviceId) {
        return Result.ok(ppidMapService.list(deviceId));
    }

    @PreAuthorize("hasAuthority('rms:integration:operate')")
    @PostMapping("/ppid-maps")
    public Result<ToolRecipeMapService.MapView> registerPpidMap(@RequestBody PpidMapReq req) {
        return Result.ok(ppidMapService.register(req.deviceId(), req.deviceTypeId(),
                req.ppidOnTool(), req.recipeId(), req.chamberId()));
    }

    @PreAuthorize("hasAuthority('rms:integration:operate')")
    @DeleteMapping("/ppid-maps/{id}")
    public Result<Void> deletePpidMap(@PathVariable String id) {
        ppidMapService.delete(id);
        return Result.ok();
    }

    // ---------- IF-E2 下发任务（阶段机回执闭环） ----------

    /** 下发任务列表（可按阶段/批次/设备过滤；{@code pendingOnly} 只留未闭环）。 */
    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/downloads")
    public Result<List<RecipeDownloadService.JobView>> downloads(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false, defaultValue = "false") boolean pendingOnly) {
        DownloadStage st = (stage == null || stage.isBlank()) ? null : DownloadStage.valueOf(stage);
        return Result.ok(downloadService.list(st, batchId, deviceId, pendingOnly));
    }

    /** 批量下发进度聚合（Req 40：逐台回执聚合）。 */
    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/downloads/batch-progress")
    public Result<RecipeDownloadService.BatchProgress> batchProgress(@RequestParam String batchId) {
        return Result.ok(downloadService.batchProgress(batchId));
    }

    /**
     * 推进下发阶段（EAP 回执）。
     *
     * <p>示例：{@code {"stage":"GRANTED"}} → {@code {"stage":"SENT_TO_TOOL","toolAck":"ACK"}}
     * → {@code {"stage":"VERIFIED","readbackHash":"<sha256>"}}。非法迁移（跳阶/乱序）直接 400。</p>
     */
    @PreAuthorize("hasAuthority('rms:integration:operate')")
    @PostMapping("/downloads/{correlationId}/ack")
    public Result<RecipeDownloadService.JobView> ackDownload(@PathVariable String correlationId,
                                                             @RequestBody DownloadAckReq req) {
        DownloadStage st = req.stage() == null || req.stage().isBlank()
                ? null : DownloadStage.valueOf(req.stage().toUpperCase());
        return Result.ok(downloadService.advance(correlationId, st, req.toolAck(),
                req.errorDesc(), req.readbackHash()));
    }

    // ---------- 接口幂等登记（运维排查 / Req §5.2） ----------

    /** 查看某接口的幂等去重登记（scope 如 IF_E1_UPLOAD）。 */
    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/idempotency")
    public Result<List<Map<String, Object>>> idempotency(@RequestParam String scope) {
        return Result.ok(idempotencyService.list(scope).stream()
                .map(r -> Map.<String, Object>of(
                        "id", r.getId(),
                        "scope", r.getScope(),
                        "idemKey", r.getIdemKey(),
                        "resultCode", r.getResultCode() == null ? "" : r.getResultCode(),
                        "createTime", r.getCreateTime() == null ? "" : r.getCreateTime().toString()))
                .toList());
    }

    // ---------- GEM 设备事件（PPChange / PPVerificationFailed） ----------

    /**
     * 接收 GEM 设备事件（EAP → RMS）。
     *
     * <p>对齐 SEMI E30 配方管理事件模型：<br>
     * {@code {"toolId":"ETCH-01","eventType":"PP_CHANGE","ppid":"ETCH_STD_01","changeType":"EDITED"}}<br>
     * {@code {"toolId":"ETCH-01","eventType":"PP_VERIFICATION_FAILED","ppid":"ETCH_STD_01","errorDesc":"STEP3 out of range"}}</p>
     */
    @PostMapping("/equipment-events")
    public Result<EquipmentEventService.EventView> equipmentEvent(
            @RequestBody EquipmentEventService.EquipmentEventReq req) {
        return Result.ok(eventService.handle(req));
    }

    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/equipment-events")
    public Result<List<EquipmentEventService.EventView>> equipmentEvents(
            @RequestParam(required = false) String toolId,
            @RequestParam(required = false) String eventType) {
        return Result.ok(eventService.list(toolId, eventType));
    }

    // ---------- 机台配方清单（S7F19 目录核对） ----------

    /**
     * 上报机台配方目录（EAP 从 S7F19/F20 取回后上报）。
     *
     * <p>{@code {"toolId":"ETCH-01","items":[{"ppid":"ETCH_STD_01","bodyHash":"<sha256>"}]}}</p>
     */
    @PostMapping("/tool-inventory")
    public Result<ToolInventoryService.InventoryReport> reportInventory(
            @RequestBody ToolInventoryService.InventoryReportReq req) {
        return Result.ok(inventoryService.report(req));
    }

    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/tool-inventory")
    public Result<List<ToolInventoryService.InventoryView>> toolInventory(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String result) {
        return Result.ok(inventoryService.list(deviceId, result));
    }

    @PreAuthorize("hasAuthority('rms:integration:view')")
    @GetMapping("/tool-inventory/summary")
    public Result<ToolInventoryService.InventorySummary> toolInventorySummary(
            @RequestParam(required = false) String deviceId) {
        return Result.ok(inventoryService.summary(deviceId));
    }

    public record ReleaseReq(String reason) {
    }

    public record PpidMapReq(String deviceId, String deviceTypeId, String ppidOnTool, String recipeId,
                             String chamberId) {
    }

    public record DownloadAckReq(String stage, String toolAck, String errorDesc, String readbackHash) {
    }
}
