package com.cim.iam.server.admin;

import com.cim.iam.server.app.AppRegistration;
import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.support.IamPermissionCodes;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色组视图（管理面）。方法级 {@code iam:role:list}。
 *
 * <p>IAM 的角色组是「ap 内粗粒度分组」，由准入分配时写入（{@code roles} 字段），
 * 故本视图不落独立字典表，而是**按 app 聚合实际在用的角色名与使用人数**，
 * 让管理员看清各接入码的角色分布（也便于对齐「角色组」约定）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + IamPermissionCodes.CONSOLE_ADMIN + "')")
public class RoleAdminController {

    private final AppRegistrationService appRegistrationService;

    public record RoleCount(String role, int userCount) {
    }

    public record AppRoleGroup(String appCode, String appName, List<RoleCount> roles) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.ROLE_LIST + "')")
    public Result<List<AppRoleGroup>> list() {
        Map<String, String> names = appRegistrationService.listApps().stream()
                .collect(Collectors.toMap(AppRegistration::getAppCode, AppRegistration::getAppName,
                        (a, b) -> a));
        Map<String, Map<String, Integer>> agg = appRegistrationService.roleAggregate();

        List<AppRoleGroup> groups = names.entrySet().stream()
                .map(e -> {
                    Map<String, Integer> counts = agg.getOrDefault(e.getKey(), Map.of());
                    List<RoleCount> roles = counts.entrySet().stream()
                            .map(c -> new RoleCount(c.getKey(), c.getValue()))
                            .toList();
                    return new AppRoleGroup(e.getKey(), e.getValue(), roles);
                })
                .sorted(java.util.Comparator.comparing(AppRoleGroup::appCode))
                .toList();
        return Result.ok(groups);
    }
}
