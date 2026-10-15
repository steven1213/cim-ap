package com.cim.rms.server.report;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 报表 / KPI API（FR-RPT1/2）。路径前缀 {@code /api/v1/rms/reports}。
 */
@RestController
@RequestMapping("/api/v1/rms/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('rms:report:view')")
public class RmsReportController {

    private final RmsReportService service;

    /** 概览 KPI 看板。 */
    @GetMapping("/dashboard")
    public Result<RmsReportService.Dashboard> dashboard() {
        return Result.ok(service.dashboard());
    }
}
