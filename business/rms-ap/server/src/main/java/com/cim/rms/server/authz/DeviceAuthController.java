package com.cim.rms.server.authz;

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
 * 设备授权 API（FR-P4，Req 11/12）。路径前缀 {@code /api/v1/rms/device-auth}。
 */
@RestController
@RequestMapping("/api/v1/rms/device-auth")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:device-auth:view')")
public class DeviceAuthController {

    private final DeviceAuthService service;

    @GetMapping
    public Result<List<DeviceAuthService.AuthView>> list(@RequestParam(required = false) String subject) {
        return Result.ok(service.list(subject));
    }

    @PreAuthorize("hasAuthority('rms:device-auth:manage')")
    @PostMapping
    public Result<DeviceAuthService.AuthView> grant(@RequestBody GrantReq req) {
        return Result.ok(service.grant(req.subject(), req.subjectType(), req.deviceId(),
                req.deviceTypeId(), req.areaId(), req.canOperate()));
    }

    @PreAuthorize("hasAuthority('rms:device-auth:manage')")
    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable String id) {
        service.revoke(id);
        return Result.ok(null);
    }

    public record GrantReq(String subject, String subjectType, String deviceId,
                           String deviceTypeId, String areaId, Boolean canOperate) {
    }
}
