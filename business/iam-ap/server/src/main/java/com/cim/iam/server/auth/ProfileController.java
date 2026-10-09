package com.cim.iam.server.auth;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.iam.server.app.AppRegistrationService;
import com.cim.spring.support.web.BizException;
import com.cim.spring.support.web.Result;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * 当前用户自助端点（需已认证）。
 *
 * <p>{@code GET /api/v1/me}：返回当前登录用户身份（uid/uname/tenant）、可进入的 ap 与按 ap 分组的角色。
 * {@code POST /api/v1/me/password}：本地凭证账号自助改密（仅 {@code local} 认证源账号支持，
 * AD/LDAP 账号改密在目录侧进行）。</p>
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class ProfileController {

    private final AppRegistrationService appRegistrationService;
    private final LocalCredentialService credentialService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<MeDto> me() {
        CimUserPrincipal principal = currentPrincipal();
        String uid = principal.userId();
        MeDto dto = new MeDto(
                uid,
                principal.username(),
                principal.tenantId(),
                appRegistrationService.enabledAppsForUser(uid),
                appRegistrationService.rolesByAppForUser(uid));
        return Result.ok(dto);
    }

    @PostMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest req) {
        CimUserPrincipal principal = currentPrincipal();
        String username = principal.username();
        if (credentialService.findByUsername(username).isEmpty()) {
            throw BizException.paramInvalid("非本地账号，改密请联系目录（AD/LDAP）管理员");
        }
        credentialService.changePassword(username, req.oldPassword(), req.newPassword());
        return Result.ok();
    }

    private CimUserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CimUserPrincipal p)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未认证");
        }
        return p;
    }

    public record MeDto(String userId, String username, String tenantId,
                       Set<String> apps, Map<String, Set<String>> roles) {
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }
}
