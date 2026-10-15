package com.cim.rms.server.qualification;

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

/**
 * 配方-机台资格矩阵 API（FR-Q1~Q4）。路径前缀 {@code /api/v1/rms/qualifications}。
 */
@RestController
@RequestMapping("/api/v1/rms/qualifications")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:qualification:view')")
public class QualificationController {

    private final QualificationService service;

    @GetMapping
    public Result<List<QualificationService.QualView>> list(@RequestParam(required = false) String recipeId,
                                                            @RequestParam(required = false) String deviceId) {
        return Result.ok(service.list(recipeId, deviceId));
    }

    @PreAuthorize("hasAuthority('rms:qualification:manage')")
    @PostMapping
    public Result<QualificationService.QualView> grant(@RequestBody GrantReq req) {
        return Result.ok(service.grant(req.recipeId(), req.versionPolicy(), req.versionId(),
                req.deviceId(), req.deviceTypeId(), req.areaId(), req.basis(),
                parse(req.effectiveFrom()), parse(req.effectiveTo())));
    }

    @PreAuthorize("hasAuthority('rms:qualification:manage')")
    @PostMapping("/{id}/revoke")
    public Result<QualificationService.QualView> revoke(@PathVariable String id) {
        return Result.ok(service.revoke(id));
    }

    @PreAuthorize("hasAuthority('rms:qualification:manage')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    /** 资格判定（IF-M1/IF-E3 复用）。 */
    @GetMapping("/check")
    public Result<QualificationService.QualCheck> check(@RequestParam String recipeId,
                                                        @RequestParam String deviceId) {
        return Result.ok(service.check(recipeId, deviceId));
    }

    private static java.time.Instant parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return java.time.Instant.parse(s.trim());
        } catch (Exception e) {
            try {
                return java.time.LocalDateTime.parse(s.trim())
                        .atZone(java.time.ZoneId.systemDefault()).toInstant();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public record GrantReq(String recipeId, String versionPolicy, String versionId,
                           String deviceId, String deviceTypeId, String areaId,
                           String basis, String effectiveFrom, String effectiveTo) {
    }
}
