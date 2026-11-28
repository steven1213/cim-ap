package com.cim.mds.server.dynamicquery;

import java.util.List;
import java.util.Map;

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
 * 动态查询定义 API（V39）——「取数契约」管理端。
 *
 * <p>路径 {@code /api/v1/mds/query-defs}；{@code GET /{id}/columns} 返回
 * dry-run 结果集元数据（ViewModel 配置列时的下拉数据源）。保存/create/update
 * 前三层验证在服务层强制执行，任何一层失败 400 带具体原因。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class QueryAdminController {

    private final QueryAdminService service;

    @PreAuthorize("hasAuthority('" + MdsPermissions.DQ_VIEW + "')")
    @GetMapping("/query-defs")
    public Result<List<QueryAdminService.QueryView>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.DQ_VIEW + "')")
    @GetMapping("/query-defs/{id}")
    public Result<QueryAdminService.QueryView> get(@PathVariable String id) {
        return Result.ok(service.get(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.DQ_VIEW + "')")
    @GetMapping("/query-defs/{id}/columns")
    public Result<List<Map<String, Object>>> columns(@PathVariable String id) {
        return Result.ok(service.columns(id));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.DQ_CREATE + "')")
    @PostMapping("/query-defs")
    public Result<QueryAdminService.QueryView> create(@RequestBody QueryAdminService.QueryReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.DQ_UPDATE + "')")
    @PutMapping("/query-defs/{id}")
    public Result<QueryAdminService.QueryView> update(
            @PathVariable String id, @RequestBody QueryAdminService.QueryReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('" + MdsPermissions.DQ_DELETE + "')")
    @DeleteMapping("/query-defs/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
