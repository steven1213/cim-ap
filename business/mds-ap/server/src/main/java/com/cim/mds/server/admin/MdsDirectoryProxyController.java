package com.cim.mds.server.admin;

import com.cim.mds.server.security.MdsPermissions;
import com.cim.spring.support.web.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织/人员目录只读代理（MDS 控制台「组织管理」「人员管理」页的数据面）。
 *
 * <p><b>组织与人员主数据只在 IAM</b>（{@code org_node} / {@code user_profile}）。业务系统按
 * {@code docs/business/iam-ap/server/identity-directory.md} 方案 A 的既定决策<b>不落用户主数据</b>，
 * 经只读 Directory API（{@code GET /api/v1/directory/**}，服务间身份 {@code X-Directory-Key}）
 * 取数——本控制器把 IAM 目录响应映射为前端六页共享组件（{@code @cim/admin}）的行形状，
 * 只读、零缓存、不落库。</p>
 *
 * <p>权限：入口码 {@code mds:org:view} / {@code mds:user:view}（组织/人员页无 BUTTON，
 * 只读无增删改——与需求「业务系统的组织/人员不能新增、编辑、删除」一致）。</p>
 */
@RestController
@RequestMapping("/api/v1/mds/admin/directory")
@lombok.RequiredArgsConstructor
public class MdsDirectoryProxyController {

    /** IAM 目录档案行 → 前端 AdminUserRow（映射时补齐凭证/锁定等业务侧恒空列）。 */
    public record DirectoryUserRow(
            String userId,
            String username,
            boolean enabled,
            boolean hasCredential,
            boolean locked,
            String lockedUntil,
            int failCount,
            String displayName,
            String employeeNo,
            String email,
            String mobile,
            String jobTitle,
            String source,
            String status,
            List<String> orgNames,
            List<String> apps,
            Map<String, List<String>> roles) {
    }

    /** 目录 API 只读客户端（六页代理与业务选人下拉共用）。 */
    private final DirectoryClient directory;

    /** 组织树（全量；前端建树）。 */
    @GetMapping("/orgs")
    @PreAuthorize("hasAuthority('" + MdsPermissions.ORG_VIEW + "')")
    public Result<List<Map<String, Object>>> orgs(
            @RequestParam(name = "since", required = false) String since) {
        return Result.ok(directory.list("/api/v1/directory/orgs", since == null ? Map.of() : Map.of("since", since)));
    }

    /** 组织成员用户 ID（缺省含子组织——与 IAM 目录语义一致）。 */
    @GetMapping("/orgs/{orgId}/users")
    @PreAuthorize("hasAuthority('" + MdsPermissions.ORG_VIEW + "')")
    public Result<List<String>> orgMembers(@PathVariable String orgId) {
        return Result.ok(directory.listStrings("/api/v1/directory/orgs/" + orgId + "/users", Map.of()));
    }

    /** 全量人员档案（映射为前端 AdminUserRow 行形状；无 username 时以 userId 补位）。 */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('" + MdsPermissions.USER_VIEW + "')")
    public Result<List<DirectoryUserRow>> users() {
        List<Map<String, Object>> profiles = directory.list("/api/v1/directory/profiles", Map.of());
        return Result.ok(profiles.stream().map(MdsDirectoryProxyController::toUserRow).toList());
    }

    // ------------------------------------------------------------------ 内部


    @SuppressWarnings("unchecked")
    private static DirectoryUserRow toUserRow(Map<String, Object> p) {
        String userId = String.valueOf(p.getOrDefault("userId", ""));
        String displayName = (String) p.get("displayName");
        List<String> orgNames = ((List<Map<String, Object>>) p.getOrDefault("orgs", List.of()))
                .stream()
                .map(o -> String.valueOf(o.getOrDefault("name", "")))
                .filter(s -> !s.isBlank())
                .toList();
        String status = (String) p.get("status");
        boolean enabled = !"INACTIVE".equals(status);
        return new DirectoryUserRow(
                userId,
                displayName != null && !displayName.isBlank() ? displayName : userId,
                enabled,
                false,
                false,
                null,
                0,
                displayName,
                (String) p.get("employeeNo"),
                (String) p.get("email"),
                (String) p.get("mobile"),
                (String) p.get("jobTitle"),
                (String) p.get("source"),
                status,
                orgNames,
                List.of(),
                new LinkedHashMap<>());
    }
}
