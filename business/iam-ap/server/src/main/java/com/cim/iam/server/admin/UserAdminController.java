package com.cim.iam.server.admin;

import com.cim.iam.server.app.EffectiveAccessResolver;
import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.auth.AccountLock;
import com.cim.iam.server.auth.AccountLockService;
import com.cim.iam.server.auth.LocalCredential;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.org.UserOrg;
import com.cim.iam.server.profile.ProfileService;
import com.cim.iam.server.profile.UserProfile;
import com.cim.iam.server.profile.UserStatus;
import com.cim.iam.server.support.IamPermissionCodes;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 用户账号与档案管理（管理面）。方法级细粒度权限码，类级 {@code iam:console:admin} 兜底。
 *
 * <p>承载本地账号生命周期（创建/启停/重置口令/解锁/删除），并<b>与用户档案合并成统一视图</b>：
 * 列表取自「本地凭证 ∪ 用户档案」的并集，因此 AD 同步来的员工（无本地凭证）也会出现，
 * 每行标注档案来源（AD 同步 / IAM 自建）与组织归属。</p>
 *
 * <p>注意：<b>准入/角色的授予仍走 {@code /api/v1/apps/*} 与组织授予</b>——本控制器只管「身份」本身，
 * 与 IAM「只管准入」的边界一致。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + IamPermissionCodes.CONSOLE_ADMIN + "')")
public class UserAdminController {

    private final LocalCredentialService credentialService;
    private final ProfileService profileService;
    private final EffectiveAccessResolver accessResolver;
    private final AccountLockService accountLockService;
    private final AuditService auditService;

    public record UserRow(
            String userId,
            String username,
            boolean enabled,
            boolean hasCredential,
            boolean locked,
            Instant lockedUntil,
            int failCount,
            String displayName,
            String employeeNo,
            String jobTitle,
            String source,
            String status,
            List<String> orgNames,
            Set<String> apps,
            Map<String, Set<String>> roles) {
    }

    public record CreateUserRequest(String username, String credential, String clientSalt,
                                    String displayName, String employeeNo, String jobTitle) {
    }

    public record StatusRequest(boolean enabled) {
    }

    public record ResetPasswordRequest(String credential, String clientSalt) {
    }

    /** 用户清单（本地凭证 ∪ 用户档案，含锁定状态、档案摘要与有效准入）。 */
    @GetMapping
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.USER_LIST + "')")
    public Result<List<UserRow>> list() {
        Map<String, LocalCredential> creds = new HashMap<>();
        for (LocalCredential c : credentialService.listAll()) {
            creds.put(c.getUserId(), c);
        }
        Map<String, UserProfile> profiles = new HashMap<>();
        for (UserProfile p : profileService.listAll()) {
            profiles.put(p.getUserId(), p);
        }
        Set<String> ids = new TreeSet<>(creds.keySet());
        ids.addAll(profiles.keySet());
        List<UserRow> rows = new ArrayList<>();
        for (String id : ids) {
            rows.add(toRow(id, creds.get(id), profiles.get(id)));
        }
        rows.sort(Comparator.comparing(UserRow::username));
        return Result.ok(rows);
    }

    /**
     * 创建本地账号（credential 为客户端第一层派生后的 clientHash）。
     *
     * <p>可同时建立用户档案（displayName / employeeNo / jobTitle）；不传则仅建凭证。</p>
     */
    @PostMapping
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.USER_CREATE + "')")
    public Result<Void> create(@RequestBody CreateUserRequest req) {
        credentialService.registerLocalUser(req.username(), req.credential(), req.clientSalt());
        if (req.displayName() != null && !req.displayName().isBlank()
                && profileService.findByUserId(req.username()).isEmpty()) {
            profileService.createManaged(req.username(), req.displayName(), req.employeeNo(),
                    null, null, req.jobTitle());
        }
        return Result.ok();
    }

    /** 启用 / 禁用账号（禁用即强制下线）。 */
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.USER_STATUS + "')")
    public Result<Void> setStatus(@PathVariable String userId, @RequestBody StatusRequest req) {
        if (credentialService.findByUserId(userId).isPresent()) {
            credentialService.setEnabled(userId, req.enabled());
        } else if (profileService.findByUserId(userId).isPresent()) {
            // AD 来源的人员：停用本地档案并强制下线（AD 侧状态由同步器覆盖）
            profileService.setStatus(userId, req.enabled() ? UserStatus.ACTIVE : UserStatus.INACTIVE);
        }
        return Result.ok();
    }

    /** 管理员重置口令（credential 为客户端第一层派生后的 clientHash；重置即强制下线）。 */
    @PutMapping("/{userId}/password")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.USER_RESET_PWD + "')")
    public Result<Void> resetPassword(@PathVariable String userId, @RequestBody ResetPasswordRequest req) {
        credentialService.resetPassword(userId, req.credential(), req.clientSalt());
        return Result.ok();
    }

    /** 解除登录锁定。 */
    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.USER_UNLOCK + "')")
    public Result<Boolean> unlock(@PathVariable String userId) {
        boolean cleared = accountLockService.unlock(userId);
        auditService.success(AuditType.USER_UNLOCKED, null, userId, cleared ? "解除登录锁定" : "无锁定记录");
        return Result.ok(cleared);
    }

    /** 删除本地账号（删除即强制下线；档案保留）。 */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.USER_DELETE + "')")
    public Result<Void> delete(@PathVariable String userId) {
        credentialService.deleteUser(userId);
        return Result.ok();
    }

    private UserRow toRow(String uid, LocalCredential c, UserProfile p) {
        String uname = c != null ? c.getUsername() : uid;
        AccountLock lock = accountLockService.find(uname).orElse(null);
        boolean locked = lock != null && lock.getLockedUntil() != null
                && lock.getLockedUntil().isAfter(Instant.now());
        boolean enabled = c != null ? c.isEnabled()
                : (p != null && p.getStatus() == UserStatus.ACTIVE);
        List<String> orgNames = new ArrayList<>();
        for (UserOrg uo : profileService.orgsOfUser(uid)) {
            String path = profileService.orgPathName(uo.getOrgId());
            orgNames.add(path != null ? path : uo.getOrgId());
        }
        return new UserRow(
                uid, uname, enabled, c != null,
                locked,
                locked ? lock.getLockedUntil() : null,
                lock == null ? 0 : lock.getFailCount(),
                p == null ? null : p.getDisplayName(),
                p == null ? null : p.getEmployeeNo(),
                p == null ? null : p.getJobTitle(),
                p == null ? "NONE" : p.getSource().name(),
                p == null ? null : p.getStatus().name(),
                orgNames,
                accessResolver.effectiveApps(uid),
                accessResolver.effectiveRoles(uid));
    }
}
