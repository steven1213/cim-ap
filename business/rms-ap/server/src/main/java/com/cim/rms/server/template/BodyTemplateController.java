package com.cim.rms.server.template;

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

import java.util.List;

/**
 * Body 模板 API（W10，对齐 camLine ROBDEF）。路径 {@code /api/v1/rms/body-templates}。
 */
@RestController
@RequestMapping("/api/v1/rms/body-templates")
@RequiredArgsConstructor
public class BodyTemplateController {

    private final BodyTemplateService service;

    @PreAuthorize("hasAuthority('rms:btemplate:view')")
    @GetMapping
    public Result<List<BodyTemplateService.TemplateSummary>> list(
            @RequestParam(required = false) String keyword) {
        return Result.ok(service.list(keyword));
    }

    @PreAuthorize("hasAuthority('rms:btemplate:view')")
    @GetMapping("/{id}")
    public Result<BodyTemplateService.TemplateDetail> get(@PathVariable String id) {
        return Result.ok(service.get(id));
    }

    /** 骨架下发（新建配方向导「应用模板」）。 */
    @PreAuthorize("hasAuthority('rms:btemplate:view')")
    @GetMapping("/{id}/skeleton")
    public Result<BodyTemplateService.Skeleton> skeleton(@PathVariable String id) {
        return Result.ok(service.skeleton(id));
    }

    @PreAuthorize("hasAuthority('rms:btemplate:manage')")
    @PostMapping
    public Result<BodyTemplateService.TemplateSummary> create(@RequestBody BodyTemplateService.TemplateUpsert req) {
        return Result.ok(service.create(req));
    }

    @PreAuthorize("hasAuthority('rms:btemplate:manage')")
    @PutMapping("/{id}")
    public Result<BodyTemplateService.TemplateSummary> update(@PathVariable String id,
                                                              @RequestBody BodyTemplateService.TemplateUpsert req) {
        return Result.ok(service.update(id, req));
    }

    @PreAuthorize("hasAuthority('rms:btemplate:manage')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }
}
