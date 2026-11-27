package com.cim.mds.server.flow;

import java.util.List;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 制造流程（Flow）API——XML 画布为源 + 解析产物表 + 图校验守门。
 *
 * <p>路径 {@code /api/v1/mds/flows}；轻量主数据不走编辑锁。
 * 画布读写契约：{@code GET /{id}} 返回主档（含 flow_xml）+ 解析产物；
 * {@code PUT /{id}/canvas} 整份 XML 回传保存（服务端解析重建产物表）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class FlowController {

    private final FlowAdminService service;
    private final FlowImportService importService;
    private final FlowEventService flowEventService;

    /** 流程列表（关键字模糊：编码/名称/技术编码）。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flows")
    public Result<List<FlowAdminService.FlowView>> flows(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    /** 变更历史（V24，P3-2）：流程域动作级事件，时间倒序，最多 200 条。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flows/{id}/events")
    public Result<List<CimFlowEventHist>> events(@PathVariable String id) {
        return Result.ok(flowEventService.listByFlow(id));
    }

    /** 流程详情（主档含画布 XML + 全部解析产物节点/边）。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flows/{id}")
    public Result<FlowAdminService.FlowDetail> detail(@PathVariable String id) {
        return Result.ok(service.detail(id));
    }

    /** 新建流程（flowCode 走 NamingRule 占号；初始无画布）。 */
    @PreAuthorize("hasAuthority('mds:flow:create')")
    @PostMapping("/flows")
    public Result<FlowAdminService.FlowView> create(
            @RequestBody FlowAdminService.FlowReq req) {
        return Result.ok(service.create(req));
    }

    /** 更新主档（仅名称/技术/备注；仅 DRAFT）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PutMapping("/flows/{id}")
    public Result<FlowAdminService.FlowView> update(
            @PathVariable String id, @RequestBody FlowAdminService.FlowReq req) {
        return Result.ok(service.update(id, req));
    }

    /** 删除流程（仅 DRAFT；解析产物一并删）。 */
    @PreAuthorize("hasAuthority('mds:flow:delete')")
    @DeleteMapping("/flows/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    /**
     * 画布保存：整份 XML 回传（渲染/修改的单一事实源），服务端解析重建解析产物表。
     * 保存即做业务结构守门；图贯通校验走 validate/publish。
     */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PutMapping("/flows/{id}/canvas")
    public Result<FlowAdminService.FlowDetail> saveCanvas(
            @PathVariable String id, @RequestBody FlowAdminService.CanvasReq req) {
        return Result.ok(service.saveXml(id, req));
    }

    /** 图校验（落库版：图结构 + ICM 约束规则全跑，写 validate_state/明细）。 */
    @PreAuthorize("hasAuthority('mds:flow:validate')")
    @PostMapping("/flows/{id}/validate")
    public Result<FlowAdminService.ValidationResult> validate(@PathVariable String id) {
        return Result.ok(service.validate(id));
    }

    /** 校验明细（报告页：rule_code/severity/message 逐条）。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flows/{id}/validate-result")
    public Result<List<CimFlowValidateResult>> validateResult(@PathVariable String id) {
        return Result.ok(service.validateResult(id));
    }

    /** 冻结（UNFROZEN → FROZEN；图校验通过才放行，内容定稿不允许修改）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flows/{id}/freeze")
    public Result<FlowAdminService.FlowView> freeze(@PathVariable String id) {
        return Result.ok(service.freeze(id));
    }

    /** 解冻（FROZEN → UNFROZEN，退回草稿可编辑）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flows/{id}/unfreeze")
    public Result<FlowAdminService.FlowView> unfreeze(@PathVariable String id) {
        return Result.ok(service.unfreeze(id));
    }

    /**
     * 激活（FROZEN → ACTIVE；图校验通过才放行；同族旧 ACTIVE 自动降级 FROZEN——
     * 单 Active 约束，生产线永远只认一条现行有效版本）。
     */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flows/{id}/activate")
    public Result<FlowAdminService.FlowView> activate(@PathVariable String id) {
        return Result.ok(service.activate(id));
    }

    /** 禁用（FROZEN/ACTIVE → DISABLED，淘汰预备删除）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flows/{id}/disable")
    public Result<FlowAdminService.FlowView> disable(@PathVariable String id) {
        return Result.ok(service.disable(id));
    }

    /** 复制为同族新版本（XML+解析产物全量带过 + assign/attr 搬运；rule 不自动复制）。 */
    @PreAuthorize("hasAuthority('mds:flow:create')")
    @PostMapping("/flows/{id}/copy")
    public Result<FlowAdminService.FlowView> copy(@PathVariable String id) {
        return Result.ok(service.copy(id));
    }

    /** 直线流程生成（P2）：按工序列自动生成 START→N×PROCESS→END 画布并保存（走 saveXml 全量守门）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flows/{id}/generate-design")
    public Result<FlowAdminService.FlowDetail> generateDesign(
            @PathVariable String id, @RequestBody FlowAdminService.GenerateDesignReq req) {
        return Result.ok(service.generateDesign(id, req));
    }

    // ---------------- 三段式 Excel 导入（P2，P07 向导后端；base64 JSON 通道，复用统一 http 封装） ----------------

    /** 下载导入模板：返回 base64 编码的 xlsx（4 sheet，含表头与示例行），前端解码构造 Blob。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flows/import-template")
    public Result<String> importTemplate() {
        return Result.ok(java.util.Base64.getEncoder()
                .encodeToString(importService.template()));
    }

    /** 导入请求体（xlsx 的 base64；dryRun=true 只校验返回预览）。 */
    public record ImportReq(String dataBase64, boolean dryRun) {
    }

    /** 导入（dryRun=true 预览：各段行数+错误清单；false 提交：事务落库，返回各段计数）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flows/import")
    public Result<Object> importExcel(@RequestBody ImportReq req) throws java.io.IOException {
        byte[] data = java.util.Base64.getDecoder().decode(req.dataBase64());
        if (req.dryRun()) {
            return Result.ok(importService.preview(data));
        }
        return Result.ok(importService.apply(data));
    }

    // ---------------- ICM（V22）：编辑锁 / 工序刷新 / 解析 / 聚合 ----------------

    /** 锁定编辑（UNFROZEN 且未锁；占用后他人不可保存画布/改覆盖数据）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flows/{id}/lock")
    public Result<FlowAdminService.FlowView> lock(@PathVariable String id) {
        return Result.ok(service.lock(id));
    }

    /** 释放编辑锁（仅锁定人本人）。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flows/{id}/unlock")
    public Result<FlowAdminService.FlowView> unlock(@PathVariable String id) {
        return Result.ok(service.unlock(id));
    }

    /** 刷新工序引用（仅 UNFROZEN+未锁）：节点指到工序族内最新 ACTIVE 版本，返回逐节点 diff。 */
    @PreAuthorize("hasAuthority('mds:flow:update')")
    @PostMapping("/flows/{id}/refresh-operations")
    public Result<List<FlowAdminService.OpRefreshDiff>> refreshOperations(
            @PathVariable String id,
            @RequestBody(required = false) List<String> stepIds) {
        return Result.ok(service.refreshOperations(id, stepIds));
    }

    /** 批量解析：全节点生效值 + 来源层级（V25 L3：product 参数非空时启用 PRODUCT 覆盖层）。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flows/{id}/effective")
    public Result<List<ContextResolver.NodeEffective>> effectiveAll(
            @PathVariable String id,
            @RequestParam(required = false) String product) {
        return Result.ok(service.effectiveAll(id, product));
    }

    /** 单节点生效值（预留 ?product= 上下文，P3 生效）。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flows/{id}/steps/{stepId}/effective")
    public Result<ContextResolver.NodeEffective> effective(
            @PathVariable String id, @PathVariable String stepId) {
        return Result.ok(service.effective(id, stepId));
    }

    /** 列表页轻量聚合（flowIds 逗号分隔）：启用业务节点数/缺指派节点数。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flows/step-flags")
    public Result<List<FlowAdminService.StepFlags>> stepFlags(
            @RequestParam String flowIds) {
        return Result.ok(service.stepFlags(List.of(flowIds.split(","))));
    }
}
