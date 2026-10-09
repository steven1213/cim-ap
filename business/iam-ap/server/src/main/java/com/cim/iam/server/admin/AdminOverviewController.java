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
import com.cim.spring.support.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台态势概览（管理面，需 {@code iam-ap:ADMIN}）。
 *
 * <p>聚合身份、接入、会话、锁定与近期审计事件，作为登录后首页的「总览」。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/overview")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('iam-ap:ADMIN')")
public class AdminOverviewController {

    private final LocalCredentialService credentialService;
    private final AppRegistrationService appRegistrationService;
    private final RefreshTokenService refreshTokenService;
    private final AccountLockService accountLockService;
    private final AuditService auditService;

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
            List<AuditEventDto> recentEvents) {
    }

    @GetMapping
    public Result<OverviewDto> overview() {
        List<LocalCredential> users = credentialService.listAll();
        int enabledUsers = (int) users.stream().filter(LocalCredential::isEnabled).count();

        List<AppRegistration> apps = appRegistrationService.listApps();
        int enabledApps = (int) apps.stream().filter(a -> a.getStatus() == AppStatus.ENABLED).count();

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
                auditService.recent(8, null).stream().map(AuditEventDto::of).toList()));
    }
}
