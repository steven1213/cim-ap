package com.cim.rms.server.effect;

import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 变更效果查询 API（W11）。路径 {@code /api/v1/rms/change-effects}。 */
@RestController
@RequestMapping("/api/v1/rms/change-effects")
@RequiredArgsConstructor
public class ChangeEffectController {

    private final ChangeEffectService service;

    @PreAuthorize("hasAuthority('rms:recipe:view')")
    @GetMapping
    public Result<List<ChangeEffectService.EffectView>> list(@RequestParam String recipeId) {
        return Result.ok(service.list(recipeId));
    }
}
