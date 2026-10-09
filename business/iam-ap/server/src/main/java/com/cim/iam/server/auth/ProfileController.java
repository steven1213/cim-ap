package com.cim.iam.server.auth;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.iam.server.app.EffectiveAccessResolver;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeRepository;
import com.cim.iam.server.org.UserOrg;
import com.cim.iam.server.profile.ProfileService;
import com.cim.iam.server.profile.UserProfile;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 当前用户自助端点（需已认证）。
 *
 * <p>{@code GET /api/v1/me}：返回当前登录用户身份（uid/uname/tenant + 档案摘要 + 组织归属）、
 * 可进入的 ap 与按 ap 分组的角色（个人授予 ∪ 组织授予）。
 * {@code POST /api/v1/me/password}：本地凭证账号自助改密（仅 {@code local} 认证源账号支持，
 * AD/LDAP 账号改密在目录侧进行）。与登录一致，请求体携带客户端第一层派生的 {@code clientHash}
 * （{@code oldCredential}/{@code newCredential}）+ 新口令随机盐 {@code newClientSalt}，明文口令不出浏览器。</p>
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class ProfileController {

    private final EffectiveAccessResolver accessResolver;
    private final ProfileService profileService;
    private final OrgNodeRepository orgNodeRepository;
    private final LocalCredentialService credentialService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<MeDto> me() {
        CimUserPrincipal principal = currentPrincipal();
        String uid = principal.userId();
        UserProfile p = profileService.findByUserId(uid).orElse(null);
        List<OrgBrief> orgs = new ArrayList<>();
        for (UserOrg uo : profileService.orgsOfUser(uid)) {
            OrgNode n = orgNodeRepository.findById(uo.getOrgId()).orElse(null);
            if (n != null) {
                orgs.add(new OrgBrief(n.getId(), n.getCode(), n.getName(),
                        n.getNodeType() == null ? null : n.getNodeType().name(), uo.isPrimary()));
            }
        }
        MeDto dto = new MeDto(
                uid,
                principal.username(),
                principal.tenantId(),
                p == null ? null : p.getDisplayName(),
                p == null ? null : p.getEmployeeNo(),
                p == null ? null : p.getJobTitle(),
                p == null ? null : p.getSource().name(),
                orgs,
                accessResolver.effectiveApps(uid),
                accessResolver.effectiveRoles(uid));
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
        credentialService.changePassword(username, req.oldCredential(), req.newCredential(), req.newClientSalt());
        return Result.ok();
    }

    private CimUserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CimUserPrincipal p)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未认证");
        }
        return p;
    }

    /** 组织摘要（用于「我的」页展示归属）。 */
    public record OrgBrief(String orgId, String code, String name, String nodeType, boolean primary) {
    }

    public record MeDto(String userId, String username, String tenantId,
                       String displayName, String employeeNo, String jobTitle, String source,
                       List<OrgBrief> orgs,
                       Set<String> apps, Map<String, Set<String>> roles) {
    }

    public record ChangePasswordRequest(String oldCredential, String newCredential, String newClientSalt) {
    }
}
