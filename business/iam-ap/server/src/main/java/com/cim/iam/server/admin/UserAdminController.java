package com.cim.iam.server.admin;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.auth.AccountLock;
import com.cim.iam.server.auth.AccountLockService;
import com.cim.iam.server.auth.LocalCredential;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户账号管理（管理面，需 {@code iam-ap:ADMIN}）。
 *
 * <p>承载本地账号的生命周期：创建、启停、重置口令、解锁、删除；并聚合其准入与锁定状态。<br>
 * 注意：<b>准入/角色的授予仍走 {@code /api/v1/apps/*}</b>（本控制器只管「身份」本身，
 * 与 IAM「只管准入、账户归属身份域」的边界一致）。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('iam-ap:ADMIN')")
public class UserAdminController {

    private final LocalCredentialService credentialService;
    private final AppRegistrationService appRegistrationService;
    private final AccountLockService accountLockService;
    private final AuditService auditService;

    public record UserRow(
            String userId,
            String username,
            boolean enabled,
            boolean locked,
            Instant lockedUntil,
            int failCount,
            Set<String> apps,
            Map<String, Set<String>> roles) {
    }

    public record CreateUserRequest(String username, String credential, String clientSalt) {
    }

    public record StatusRequest(boolean enabled) {
    }

    public record ResetPasswordRequest(String credential, String clientSalt) {
    }

    /** 本地账号清单（含锁定状态与准入摘要）。 */
    @GetMapping
    public Result<List<UserRow>> list() {
        List<UserRow> rows = credentialService.listAll().stream()
                .map(this::toRow)
                .sorted(Comparator.comparing(UserRow::username))
                .toList();
        return Result.ok(rows);
    }

    /** 创建本地账号（credential 为客户端第一层派生后的 clientHash）。 */
    @PostMapping
    public Result<Void> create(@RequestBody CreateUserRequest req) {
        credentialService.registerLocalUser(req.username(), req.credential(), req.clientSalt());
        return Result.ok();
    }

    /** 启用 / 禁用账号（禁用即强制下线）。 */
    @PutMapping("/{userId}/status")
    public Result<Void> setStatus(@PathVariable String userId, @RequestBody StatusRequest req) {
        credentialService.setEnabled(userId, req.enabled());
        return Result.ok();
    }

    /** 管理员重置口令（credential 为客户端第一层派生后的 clientHash；重置即强制下线）。 */
    @PutMapping("/{userId}/password")
    public Result<Void> resetPassword(@PathVariable String userId, @RequestBody ResetPasswordRequest req) {
        credentialService.resetPassword(userId, req.credential(), req.clientSalt());
        return Result.ok();
    }

    /** 解除登录锁定。 */
    @PostMapping("/{userId}/unlock")
    public Result<Boolean> unlock(@PathVariable String userId) {
        boolean cleared = accountLockService.unlock(userId);
        auditService.success(AuditType.USER_UNLOCKED, null, userId, cleared ? "解除登录锁定" : "无锁定记录");
        return Result.ok(cleared);
    }

    /** 删除本地账号（删除即强制下线）。 */
    @DeleteMapping("/{userId}")
    public Result<Void> delete(@PathVariable String userId) {
        credentialService.deleteUser(userId);
        return Result.ok();
    }

    private UserRow toRow(LocalCredential c) {
        String uid = c.getUserId();
        String uname = c.getUsername();
        AccountLock lock = accountLockService.find(uname).orElse(null);
        boolean locked = lock != null && lock.getLockedUntil() != null
                && lock.getLockedUntil().isAfter(Instant.now());
        Set<String> apps = appRegistrationService.enabledAppsForUser(uid);
        Map<String, Set<String>> roles = appRegistrationService.rolesByAppForUser(uid);
        return new UserRow(
                uid, uname, c.isEnabled(),
                locked,
                locked ? lock.getLockedUntil() : null,
                lock == null ? 0 : lock.getFailCount(),
                apps, roles);
    }
}
