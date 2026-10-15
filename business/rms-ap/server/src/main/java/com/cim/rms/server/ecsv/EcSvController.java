package com.cim.rms.server.ecsv;

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

import java.util.List;
import java.util.Map;

/**
 * EC/SV 规格与比对 API（FR-D2~D4，Req 43/44/45）。路径前缀 {@code /api/v1/rms/ecsv}。
 */
@RestController
@RequestMapping("/api/v1/rms/ecsv")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:ecsv:view')")
public class EcSvController {

    private final EcSvService service;

    @GetMapping("/specs")
    public Result<List<EcSvService.SpecView>> listSpecs(@RequestParam(required = false) String scope,
                                                        @RequestParam(required = false) String refId) {
        return Result.ok(service.listSpecs(scope, refId));
    }

    @PreAuthorize("hasAuthority('rms:ecsv:manage')")
    @PostMapping("/specs")
    public Result<EcSvService.SpecView> createSpec(@RequestBody EcSvService.SpecInput in) {
        return Result.ok(service.saveSpec(null, in));
    }

    @PreAuthorize("hasAuthority('rms:ecsv:manage')")
    @PutMapping("/specs/{id}")
    public Result<EcSvService.SpecView> updateSpec(@PathVariable String id,
                                                  @RequestBody EcSvService.SpecInput in) {
        return Result.ok(service.saveSpec(id, in));
    }

    @PreAuthorize("hasAuthority('rms:ecsv:manage')")
    @DeleteMapping("/specs/{id}")
    public Result<Void> deleteSpec(@PathVariable String id) {
        service.deleteSpec(id);
        return Result.ok(null);
    }

    @GetMapping("/summary")
    public Result<EcSvService.Summary> summary() {
        return Result.ok(service.summary());
    }

    @GetMapping("/values")
    public Result<List<EcSvService.ValueView>> listValues(@RequestParam(required = false) String deviceId,
                                                          @RequestParam(defaultValue = "200") int limit) {
        return Result.ok(service.listValues(deviceId, limit));
    }

    /** EAP 上报并校验 EC/SV（IF-E5）。 */
    @PreAuthorize("hasAuthority('rms:ecsv:manage')")
    @PostMapping("/upload")
    public Result<EcSvService.UploadResult> upload(@RequestBody UploadReq req) {
        return Result.ok(service.upload(req.deviceId(), req.scope(), req.refId(), req.ecsvType(), req.values()));
    }

    public record UploadReq(String deviceId, String scope, String refId, String ecsvType,
                            Map<String, String> values) {
    }
}
