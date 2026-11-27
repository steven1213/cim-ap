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
import org.springframework.web.bind.annotation.RestController;

/**
 * 参数定义字典 API（mds:paramDef:view/create/update/delete 四动作）。
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ParamDefController {

    private final ParamDefAdminService service;

    @PreAuthorize("hasAuthority('mds:paramDef:view')")
    @GetMapping("/param-defs")
    public Result<List<CimParamDef>> list() {
        return Result.ok(service.list());
    }

    @PreAuthorize("hasAuthority('mds:paramDef:create')")
    @PostMapping("/param-defs")
    public Result<CimParamDef> create(@RequestBody ParamDefAdminService.ParamDefReq req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('mds:paramDef:update')")
    @PutMapping("/param-defs/{id}")
    public Result<CimParamDef> update(
            @PathVariable String id, @RequestBody ParamDefAdminService.ParamDefReq req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('mds:paramDef:delete')")
    @DeleteMapping("/param-defs/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
