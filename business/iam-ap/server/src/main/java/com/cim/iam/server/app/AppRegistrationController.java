package com.cim.iam.server.app;

import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * ap 注册与准入管理端点（IAM 管理面）。
 *
 * <p>业务 ap（mds-ap / mes-ap …）在此登记接入码，并授予用户进入资格；
 * 令牌签发时读取 {@link AppRegistrationService#enabledAppsForUser} 作为 {@code apps} claim。</p>
 */
@RestController
@RequestMapping("/api/v1/apps")
@RequiredArgsConstructor
public class AppRegistrationController {

    private final AppRegistrationService service;

    public record RegisterAppRequest(String appCode, String appName, int sortNo) {}
    public record UpdateAppRequest(String appName, AppStatus status) {}
    public record AssignRequest(String userId, Set<String> roles) {}

    @PostMapping
    public Result<AppRegistration> register(@RequestBody RegisterAppRequest req) {
        return Result.ok(service.registerApp(req.appCode(), req.appName(), req.sortNo()));
    }

    @GetMapping
    public Result<List<AppRegistration>> list() {
        return Result.ok(service.listApps());
    }

    @PutMapping("/{appCode}")
    public Result<AppRegistration> update(@PathVariable String appCode,
                                         @RequestBody UpdateAppRequest req) {
        return Result.ok(service.updateApp(appCode, req.appName(), req.status()));
    }

    @PostMapping("/{appCode}/users")
    public Result<Void> assign(@PathVariable String appCode, @RequestBody AssignRequest req) {
        service.assignUserToApp(req.userId(), appCode, req.roles());
        return Result.ok();
    }

    @DeleteMapping("/{appCode}/users/{userId}")
    public Result<Void> revoke(@PathVariable String appCode, @PathVariable String userId) {
        service.revokeUserFromApp(userId, appCode);
        return Result.ok();
    }

    @GetMapping("/users/{userId}/apps")
    public Result<Set<String>> appsForUser(@PathVariable String userId) {
        return Result.ok(service.enabledAppsForUser(userId));
    }
}
