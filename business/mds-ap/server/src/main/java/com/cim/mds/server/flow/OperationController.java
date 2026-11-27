package com.cim.mds.server.flow;

import java.util.List;

import com.cim.mds.server.security.MdsPermissions;
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
 * 工序库（Operation）API——四层工艺域建模 O 层（2026-11-18 设计定稿）。
 *
 * <p>路径 {@code /api/v1/mds/operations}；轻量主数据不走编辑锁。
 * 生命周期端点与 Flow 同款：freeze/unfreeze/activate/disable + copy。
 * {@code GET /active} 供流程画布「选工序」下拉（仅 ACTIVE）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class OperationController {

    private final OperationAdminService service;

    /** 工序列表（关键字模糊：编码/名称/工艺编码）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_VIEW + "')")
    @GetMapping("/operations")
    public Result<List<OperationAdminService.OpView>> list(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    /** 生效工序清单（路线画布「选工序」下拉用；仅 ACTIVE）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_VIEW + "')")
    @GetMapping("/operations/active")
    public Result<List<OperationAdminService.OpView>> listActive() {
        return Result.ok(service.listActive());
    }

    /** 工序详情。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_VIEW + "')")
    @GetMapping("/operations/{id}")
    public Result<OperationAdminService.OpView> detail(@PathVariable String id) {
        return Result.ok(service.detail(id));
    }

    /** 新建工序（opCode 手填全局唯一；初始 UNFROZEN）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_CREATE + "')")
    @PostMapping("/operations")
    public Result<OperationAdminService.OpView> create(
            @RequestBody OperationAdminService.OpReq req) {
        return Result.ok(service.create(req));
    }

    /** 更新工序（仅 UNFROZEN；opCode/opType 不可改）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_UPDATE + "')")
    @PutMapping("/operations/{id}")
    public Result<OperationAdminService.OpView> update(
            @PathVariable String id, @RequestBody OperationAdminService.OpReq req) {
        return Result.ok(service.update(id, req));
    }

    /** 删除工序（仅 UNFROZEN/DISABLED；被路线步骤引用禁删）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_DELETE + "')")
    @DeleteMapping("/operations/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    /** 冻结：UNFROZEN → FROZEN。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_UPDATE + "')")
    @PostMapping("/operations/{id}/freeze")
    public Result<OperationAdminService.OpView> freeze(@PathVariable String id) {
        return Result.ok(service.freeze(id));
    }

    /** 解冻：FROZEN → UNFROZEN。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_UPDATE + "')")
    @PostMapping("/operations/{id}/unfreeze")
    public Result<OperationAdminService.OpView> unfreeze(@PathVariable String id) {
        return Result.ok(service.unfreeze(id));
    }

    /** 激活：FROZEN → ACTIVE（同族唯一；旧 ACTIVE 自动降级 FROZEN）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_UPDATE + "')")
    @PostMapping("/operations/{id}/activate")
    public Result<OperationAdminService.OpView> activate(@PathVariable String id) {
        return Result.ok(service.activate(id));
    }

    /** 禁用：FROZEN/ACTIVE → DISABLED（被引用禁禁用）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_UPDATE + "')")
    @PostMapping("/operations/{id}/disable")
    public Result<OperationAdminService.OpView> disable(@PathVariable String id) {
        return Result.ok(service.disable(id));
    }

    /** 复制为同族新版本（opCode 手填必填；冻结/生效版本的改动一律走复制）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_CREATE + "')")
    @PostMapping("/operations/{id}/copy")
    public Result<OperationAdminService.OpView> copy(
            @PathVariable String id, @RequestParam String newOpCode) {
        return Result.ok(service.copy(id, newOpCode));
    }

    // ---------------- 可变参数（V29：Wafer Recipe 层落点，仅 UNFROZEN 可维护） ----------------

    /** 工序可变参数列表。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_VIEW + "')")
    @GetMapping("/operations/{id}/variable-params")
    public Result<List<OperationAdminService.VarParamView>> listVarParams(@PathVariable String id) {
        return Result.ok(service.listVarParams(id));
    }

    /** 可变参数整单替换（仅 UNFROZEN；param_key 引用参数定义；与主字段同口径走复制改版）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.OPERATION_UPDATE + "')")
    @PutMapping("/operations/{id}/variable-params")
    public Result<List<OperationAdminService.VarParamView>> saveVarParams(
            @PathVariable String id, @RequestBody List<OperationAdminService.VarParamReq> items) {
        return Result.ok(service.saveVarParams(id, items));
    }
}
