package com.cim.mds.server.flow;

import com.cim.mds.server.security.MdsPermissions;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配方解算契约 API（V29，recipe-resolve-design.md §5）——WIP/MES 进站前的
 * 唯一解算入口；BLOCKED 以 HTTP 200 + resolved=false + reason 返回。
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class RecipeResolveController {

    private final RecipeResolveService service;

    /** 配方解算（快照 / BLOCKED+原因码）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.RECIPE_RESOLVE + "')")
    @PostMapping("/recipe/resolve")
    public Result<RecipeResolveService.ResolveView> resolve(
            @RequestBody RecipeResolveService.ResolveReq req) {
        return Result.ok(service.resolve(req));
    }
}
