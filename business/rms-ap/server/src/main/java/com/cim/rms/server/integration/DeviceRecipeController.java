package com.cim.rms.server.integration;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备视角 API：机台 → 配方列表（W7）。
 *
 * <p>单独建控制器（而非并入 {@code DeviceAdminController}）是为了避免
 * {@code device → integration} 的包循环依赖：聚合服务落在 integration 包。</p>
 */
@RestController
@RequestMapping("/api/v1/rms")
@RequiredArgsConstructor
public class DeviceRecipeController {

    private final DeviceRecipeViewService service;

    /** 该机台的配方列表（四路来源合并 + 孤儿/漂移标注）。 */
    @PreAuthorize("hasAuthority('rms:recipe:view')")
    @GetMapping("/devices/{id}/recipes")
    public Result<DeviceRecipeViewService.DeviceRecipeView> deviceRecipes(@PathVariable String id) {
        return Result.ok(service.view(id));
    }
}
