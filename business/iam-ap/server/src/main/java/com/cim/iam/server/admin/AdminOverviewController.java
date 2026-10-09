package com.cim.iam.server.admin;

import com.cim.iam.server.app.AppRegistration;
import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.app.AppStatus;
import com.cim.iam.server.audit.AuditEventDto;
import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.auth.AccountLockService;
import com.cim.iam.server.auth.LocalCredential;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.auth.RefreshTokenService;
import com.cim.iam.server.common.DataOrigin;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeService;
import com.cim.iam.server.profile.ProfileService;
import com.cim.iam.server.profile.UserProfile;
import com.cim.iam.server.support.IamPermissionCodes;
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台态势概览（管理面）。
 *
 * <p><b>鉴权：方法级 {@code iam:overview:view}，类级 {@code iam:console:admin} 兜底</b>
 * （方法级优先）。控制台内部权限由本 ap 库内 RBAC 决定，见
 * {@code console-menu-perm-i18n.md} §4。</p>
 *
 * <p>聚合身份、接入、会话、锁定与近期审计事件，作为登录后首页的「总览」。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/overview")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + IamPermissionCodes.CONSOLE_ADMIN + "')")
public class AdminOverviewController {

    private final LocalCredentialService credentialService;
    private final AppRegistrationService appRegistrationService;
    private final RefreshTokenService refreshTokenService;
    private final AccountLockService accountLockService;
    private final AuditService auditService;
    private final OrgNodeService orgNodeService;
    private final ProfileService profileService;

    public record OverviewDto(
            int userCount,
            int enabledUserCount,
            int disabledUserCount,
            int appCount,
            int enabledAppCount,
            int activeSessionCount,
            int lockedCount,
            long loginSuccessCount,
            long loginFailureCount,
            int orgCount,
            int orgManagedCount,
            int profileCount,
            int adProfileCount,
            List<AuditEventDto> recentEvents) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.OVERVIEW_VIEW + "')")
    public Result<OverviewDto> overview() {
        List<LocalCredential> users = credentialService.listAll();
        int enabledUsers = (int) users.stream().filter(LocalCredential::isEnabled).count();

        List<AppRegistration> apps = appRegistrationService.listApps();
        int enabledApps = (int) apps.stream().filter(a -> a.getStatus() == AppStatus.ENABLED).count();

        List<OrgNode> orgs = orgNodeService.listAll();
        int managedOrgs = (int) orgs.stream()
                .filter(o -> o.getSource() == DataOrigin.IAM_MANAGED).count();
        List<UserProfile> profiles = profileService.listAll();
        int adProfiles = (int) profiles.stream()
                .filter(p -> p.getSource() == DataOrigin.AD_SYNCED).count();

        return Result.ok(new OverviewDto(
                users.size(),
                enabledUsers,
                users.size() - enabledUsers,
                apps.size(),
                enabledApps,
                refreshTokenService.listActiveSessions().size(),
                accountLockService.listActiveLocks().size(),
                auditService.countByType(AuditType.LOGIN_SUCCESS),
                auditService.countByType(AuditType.LOGIN_FAILURE),
                orgs.size(),
                managedOrgs,
                profiles.size(),
                adProfiles,
                auditService.recent(8, null).stream().map(AuditEventDto::of).toList()));
    }
}
