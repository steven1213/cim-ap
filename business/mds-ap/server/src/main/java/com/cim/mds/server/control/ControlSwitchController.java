package com.cim.mds.server.control;

import java.util.List;

import com.cim.mds.server.security.MdsPermissions;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 卡控开关 API（V18）——{@code /api/v1/mds/control-switches}。
 *
 * <p>权限口径：查询沿用 {@code mds:shift:view}（开关现状随排班页对有视图权的人可见）、
 * 启停沿用 {@code mds:shift:update}（方案定稿：不另立权限码，维护人是同一批管理员）。
 * 无独立管理页——开关 UI 就地嵌在各域业务页，本控制器是页面与消费方（MES 服务间）
 * 共用的数据通道。</p>
 */
@RestController
@RequestMapping("/api/v1/mds")
@RequiredArgsConstructor
public class ControlSwitchController {

    private final ControlSwitchService service;

    /** 开关清单（domainCode 过滤可选；消费方 TTL 缓存按域拉取）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_VIEW + "')")
    @GetMapping("/control-switches")
    public Result<List<ControlSwitchService.SwitchView>> list(
            @RequestParam(required = false) String domainCode) {
        return Result.ok(service.list(domainCode));
    }

    /** 启停切换（幂等：与现值相同直接返回）。 */
    @PreAuthorize("hasAuthority('" + MdsPermissions.SHIFT_UPDATE + "')")
    @PutMapping("/control-switches/{code}")
    public Result<ControlSwitchService.SwitchView> setEnabled(
            @PathVariable String code, @RequestParam boolean enabled) {
        return Result.ok(service.setEnabled(code, enabled));
    }
}
