package com.cim.rms.server.bypass;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.Instant;
import java.util.List;

/**
 * Bypass 豁免 API（FR-B1~B3，Req 22/23）。路径前缀 {@code /api/v1/rms/bypasses}。
 */
@RestController
@RequestMapping("/api/v1/rms/bypasses")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:bypass:view')")
public class BypassController {

    private final BypassService service;

    @GetMapping
    public Result<List<BypassService.BypassView>> list() {
        return Result.ok(service.list());
    }

    @PreAuthorize("hasAuthority('rms:bypass:manage')")
    @PostMapping
    public Result<BypassService.BypassView> create(@RequestBody CreateReq req) {
        return Result.ok(service.create(req.scope(), req.targetId(), req.bypassType(), req.reason(),
                parse(req.effectiveFrom()), parse(req.effectiveTo())));
    }

    @PreAuthorize("hasAuthority('rms:bypass:manage')")
    @PutMapping("/{id}/active")
    public Result<BypassService.BypassView> setActive(@PathVariable String id, @RequestParam boolean active) {
        return Result.ok(service.setActive(id, active));
    }

    @PreAuthorize("hasAuthority('rms:bypass:manage')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok(null);
    }

    private static Instant parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(s.trim());
        } catch (Exception e) {
            try {
                return java.time.LocalDateTime.parse(s.trim())
                        .atZone(java.time.ZoneId.systemDefault()).toInstant();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public record CreateReq(String scope, String targetId, String bypassType, String reason,
                            String effectiveFrom, String effectiveTo) {
    }
}
