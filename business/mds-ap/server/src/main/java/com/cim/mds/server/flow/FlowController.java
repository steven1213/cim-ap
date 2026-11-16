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

    /** 流程列表（关键字模糊：编码/名称/技术编码）。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @GetMapping("/flows")
    public Result<List<FlowAdminService.FlowView>> flows(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
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

    /** 图校验（只读，返回问题清单；画布「校验」按钮）。 */
    @PreAuthorize("hasAuthority('mds:flow:view')")
    @PostMapping("/flows/{id}/validate")
    public Result<FlowAdminService.ValidationResult> validate(@PathVariable String id) {
        return Result.ok(service.validate(id));
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

    /** 复制为同族新版本（XML+解析产物全量带过；新 UNFROZEN，flowCode 重新占号，版本号族内递增）。 */
    @PreAuthorize("hasAuthority('mds:flow:create')")
    @PostMapping("/flows/{id}/copy")
    public Result<FlowAdminService.FlowView> copy(@PathVariable String id) {
        return Result.ok(service.copy(id));
    }
}
