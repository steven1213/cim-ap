package com.cim.mds.server.dynamicquery;

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
 * 视图模型 API（V39）——「UI 渲染契约」管理端。
 *
 * <p>路径 {@code /api/v1/mds/view-models}；头+列一次提交（列全量替换），
 * 保存时对所属 Query dry-run 结果集做 col_key 硬校验。运行时契约端点在
 * {@link DynamicViewController}。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ViewModelAdminController {

    private final ViewModelAdminService service;

    @PreAuthorize("hasAuthority('" + MdsPermissions.VM_VIEW + "')")
    @GetMapping("/view-models")
    public Result<List<ViewModelAdminService.ViewModelView>> list(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.VM_VIEW + "')")
    @GetMapping("/view-models/{id}")
    public Result<ViewModelAdminService.ViewModelView> get(@PathVariable String id) {
        return Result.ok(service.get(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.VM_CREATE + "')")
    @PostMapping("/view-models")
    public Result<ViewModelAdminService.ViewModelView> create(
            @RequestBody ViewModelAdminService.ViewModelReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.VM_UPDATE + "')")
    @PutMapping("/view-models/{id}")
    public Result<ViewModelAdminService.ViewModelView> update(
            @PathVariable String id, @RequestBody ViewModelAdminService.ViewModelReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.VM_DELETE + "')")
    @DeleteMapping("/view-models/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
