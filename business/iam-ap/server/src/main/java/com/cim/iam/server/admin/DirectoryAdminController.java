package com.cim.iam.server.admin;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.directory.AdDirectorySyncService;
import com.cim.iam.server.directory.DirectoryDtos.WatermarkDto;
import com.cim.iam.server.directory.DirectoryService;
import com.cim.iam.server.org.OrgAppAssignment;
import com.cim.iam.server.org.OrgGrantService;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeService;
import com.cim.iam.server.profile.ProfileService;
import com.cim.iam.server.profile.UserProfile;
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
import java.util.List;
import java.util.Set;

/**
 * 档案 / 组织授予 / 目录同步管理（管理面，identity-directory.md §5.2）。
 *
 * <p>方法级细粒度权限码；类级 {@code iam:console:admin} 兜底。</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + IamPermissionCodes.CONSOLE_ADMIN + "')")
public class DirectoryAdminController {

    private final ProfileService profileService;
    private final OrgGrantService orgGrantService;
    private final OrgNodeService orgNodeService;
    private final AppRegistrationService appRegistrationService;
    private final AdDirectorySyncService adSyncService;
    private final DirectoryService directoryService;

    public record ProfileRow(String userId, String employeeNo, String displayName, String email,
                            String mobile, String jobTitle, String status, String source, Instant syncedAt,
                            List<String> orgNames) {
    }

    public record CreateProfileRequest(String userId, String displayName, String employeeNo,
                                       String email, String mobile, String jobTitle) {
    }

    public record UpdateProfileRequest(String displayName, String email, String mobile, String jobTitle) {
    }

    public record GrantRequest(String orgId, String appCode, Set<String> roles, boolean includeChildren) {
    }

    public record OrgGrantRow(String orgId, String orgName, String orgCode, String appCode,
                              Set<String> roles, boolean includeChildren, String status) {
    }

    // ---------- 档案 ----------

    @GetMapping("/profiles")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.USER_LIST + "')")
    public Result<List<ProfileRow>> profiles() {
        return Result.ok(profileService.listAll().stream().map(this::toRow).toList());
    }

    /** 新建 IAM 自建档案（设备厂商 / 访客 / 服务账号）。 */
    @PostMapping("/profiles")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.USER_CREATE + "')")
    public Result<Void> createProfile(@RequestBody CreateProfileRequest req) {
        profileService.createManaged(req.userId(), req.displayName(), req.employeeNo(),
                req.email(), req.mobile(), req.jobTitle());
        return Result.ok();
    }

    /** 更新档案（AD 来源仅可改岗位等 IAM 属性）。 */
    @PutMapping("/profiles/{userId}")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.USER_UPDATE + "')")
    public Result<Void> updateProfile(@PathVariable String userId, @RequestBody UpdateProfileRequest req) {
        profileService.updateProfile(userId, req.displayName(), req.email(), req.mobile(), req.jobTitle());
        return Result.ok();
    }

    // ---------- 组织授予 ----------

    @GetMapping("/org-grants")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.GRANT_LIST + "')")
    public Result<List<OrgGrantRow>> grants() {
        return Result.ok(orgGrantService.listAll().stream().map(this::toRow).toList());
    }

    /** 授予组织级准入（组织 → ap + 粗角色组）。 */
    @PostMapping("/org-grants")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.GRANT_GRANT + "')")
    public Result<Void> grant(@RequestBody GrantRequest req) {
        appRegistrationService.requireApp(req.appCode()); // 校验接入码已登记
        orgGrantService.grant(req.orgId(), req.appCode(), req.roles(), req.includeChildren());
        return Result.ok();
    }

    @DeleteMapping("/org-grants/{orgId}/{appCode}")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.GRANT_REVOKE + "')")
    public Result<Void> revoke(@PathVariable String orgId, @PathVariable String appCode) {
        orgGrantService.revoke(orgId, appCode);
        return Result.ok();
    }

    // ---------- 目录同步 ----------

    /** 当前水位（供排障：业务侧比对值）。 */
    @GetMapping("/sync/watermark")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.SETTINGS_VIEW + "')")
    public Result<WatermarkDto> watermark() {
        return Result.ok(directoryService.watermark());
    }

    /** 手动触发一次 AD 同步；未配置 AD 时返回 {@code skipped=true}（正常态）。 */
    @PostMapping("/sync/ad")
    @PreAuthorize("hasAuthority('" + IamPermissionCodes.SYNC_RUN + "')")
    public Result<AdDirectorySyncService.SyncResult> syncAd() {
        return Result.ok(adSyncService.syncNow());
    }

    private ProfileRow toRow(UserProfile p) {
        List<String> orgNames = profileService.orgsOfUser(p.getUserId()).stream()
                .map(uo -> {
                    String path = profileService.orgPathName(uo.getOrgId());
                    return path != null ? path : uo.getOrgId();
                })
                .toList();
        return new ProfileRow(p.getUserId(), p.getEmployeeNo(), p.getDisplayName(), p.getEmail(),
                p.getMobile(), p.getJobTitle(), p.getStatus().name(), p.getSource().name(),
                p.getSyncedAt(), orgNames);
    }

    private OrgGrantRow toRow(OrgAppAssignment g) {
        OrgNode org = orgNodeService.find(g.getOrgId()).orElse(null);
        return new OrgGrantRow(g.getOrgId(),
                org == null ? null : org.getName(),
                org == null ? null : org.getCode(),
                g.getAppCode(),
                roleSet(g.getRoles()),
                g.isIncludeChildren(),
                g.getStatus().name());
    }

    private static Set<String> roleSet(String roles) {
        Set<String> set = new java.util.LinkedHashSet<>();
        if (roles == null || roles.isBlank()) {
            return set;
        }
        for (String r : roles.split(",")) {
            String v = r.trim();
            if (!v.isEmpty()) {
                set.add(v);
            }
        }
        return set;
    }
}
