package com.cim.iam.server.admin;

import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.auth.AccountLockService;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 登录锁定管理（管理面，需 {@code iam-ap:ADMIN}）。
 *
 * <p>列出当前处于锁定状态的用户名（暴力破解防护触发），支持管理员手动解锁。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/lockouts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('iam-ap:ADMIN')")
public class LockoutAdminController {

    private final AccountLockService accountLockService;
    private final AuditService auditService;

    public record LockRow(String username, int failCount, Instant firstFailAt, Instant lastFailAt, Instant lockedUntil) {
    }

    /** 当前锁定中的账号（lockedUntil 升序）。 */
    @GetMapping
    public Result<List<LockRow>> list() {
        List<LockRow> rows = accountLockService.listActiveLocks().stream()
                .map(l -> new LockRow(l.getUsername(), l.getFailCount(),
                        l.getFirstFailAt(), l.getLastFailAt(), l.getLockedUntil()))
                .toList();
        return Result.ok(rows);
    }

    /** 手动解锁。 */
    @DeleteMapping("/{username}")
    public Result<Boolean> unlock(@PathVariable String username) {
        boolean cleared = accountLockService.unlock(username);
        auditService.success(AuditType.USER_UNLOCKED, null, username,
                cleared ? "管理员手动解锁" : "无锁定记录");
        return Result.ok(cleared);
    }
}
