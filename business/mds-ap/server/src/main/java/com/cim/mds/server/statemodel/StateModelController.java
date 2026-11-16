package com.cim.mds.server.statemodel;

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
 * 状态模型 API——「一类对象的状态机」注册表 + 转换 allowlist 维护
 * （SEMI E30 GEM 状态模型方法论口径）。
 *
 * <p>路径 {@code /api/v1/mds/state-models}；轻量规则表不走编辑锁。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class StateModelController {

    private final StateModelAdminService service;

    /** 新建状态模型（modelCode/dictCode/objectType 建后不可改）。 */
    @PreAuthorize("hasAuthority('mds:stateModel:create')")
    @PostMapping("/state-models")
    public Result<StateModelAdminService.StateModelView> create(
            @RequestBody StateModelAdminService.StateModelReq req) {
        return Result.ok(service.create(req));
    }

    /** 更新模型（只吃 modelName/unitForm/stateCodes；状态收缩有转换引用守门）。 */
    @PreAuthorize("hasAuthority('mds:stateModel:update')")
    @PutMapping("/state-models/{modelCode}")
    public Result<StateModelAdminService.StateModelView> update(
            @PathVariable String modelCode,
            @RequestBody StateModelAdminService.StateModelReq req) {
        return Result.ok(service.update(modelCode, req));
    }

    /** 删除模型（存在转换规则时拒绝）。 */
    @PreAuthorize("hasAuthority('mds:stateModel:delete')")
    @DeleteMapping("/state-models/{modelCode}")
    public Result<Void> delete(@PathVariable String modelCode) {
        service.delete(modelCode);
        return Result.ok(null);
    }

    /** 全部状态模型（objectType/unitForm 供前端识别模型归属）。 */
    @PreAuthorize("hasAuthority('mds:stateModel:view')")
    @GetMapping("/state-models")
    public Result<List<StateModelAdminService.StateModelView>> models() {
        return Result.ok(service.listModels());
    }

    /** 按对象域 + 子单元形态解析模型（表单按 unitType 拉状态集/合法项用）。 */
    @PreAuthorize("hasAuthority('mds:stateModel:view')")
    @GetMapping("/state-models/resolve")
    public Result<StateModelAdminService.StateModelView> resolve(
            @RequestParam String objectType,
            @RequestParam(required = false) String unitForm) {
        return Result.ok(service.resolve(objectType, unitForm));
    }

    /** 某模型的全部转换规则（矩阵初始勾选态 + 触发说明表）。 */
    @PreAuthorize("hasAuthority('mds:stateModel:view')")
    @GetMapping("/state-models/{modelCode}/transitions")
    public Result<List<StateModelAdminService.TransitionView>> transitions(
            @PathVariable String modelCode) {
        return Result.ok(service.transitions(modelCode));
    }

    /** 从某状态出发的合法目标列表（编辑表单状态下拉只列可达项）。 */
    @PreAuthorize("hasAuthority('mds:stateModel:view')")
    @GetMapping("/state-models/{modelCode}/allowed")
    public Result<List<String>> allowed(@PathVariable String modelCode,
                                        @RequestParam String from) {
        return Result.ok(service.allowedTargets(modelCode, from));
    }

    /** 矩阵勾选/取消 + 触发说明维护（取消勾选返回 data=null）。 */
    @PreAuthorize("hasAuthority('mds:stateModel:update')")
    @PutMapping("/state-models/{modelCode}/transitions")
    public Result<StateModelAdminService.TransitionView> setTransition(
            @PathVariable String modelCode,
            @RequestBody StateModelAdminService.TransitionReq req) {
        return Result.ok(service.setTransition(modelCode, req));
    }
}
