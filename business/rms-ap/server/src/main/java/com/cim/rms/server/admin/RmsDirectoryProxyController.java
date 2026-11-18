package com.cim.rms.server.admin;

import com.cim.rms.server.security.RmsPermissions;
import com.cim.spring.support.web.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织/人员目录只读代理（RMS 控制台「组织管理」「人员管理」页的数据面）。
 *
 * <p><b>组织与人员主数据只在 IAM</b>（{@code org_node} / {@code user_profile}）。业务系统按
 * {@code docs/business/iam-ap/server/identity-directory.md} 方案 A 的既定决策<b>不落用户主数据</b>，
 * 经只读 Directory API（{@code GET /api/v1/directory/**}，服务间身份 {@code X-Directory-Key}）
 * 取数——本控制器把 IAM 目录响应映射为前端六页共享组件（{@code @cim/admin}）的行形状，
 * 只读、零缓存、不落库。</p>
 *
 * <p>权限：入口码 {@code rms:org:view} / {@code rms:user:view}（组织/人员页无 BUTTON，
 * 只读无增删改——与需求「业务系统的组织/人员不能新增、编辑、删除」一致）。</p>
 */
@RestController
@RequestMapping("/api/v1/rms/admin/directory")
public class RmsDirectoryProxyController {

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

    private final RestTemplate restTemplate;

    /** IAM 基地址（本地默认 8081；生产按部署注入）。 */
    private final String iamBaseUrl;

    /** 目录 API 服务身份密钥（与 IAM {@code cim.iam.directory.api.key} 同值，可轮换）。 */
    private final String directoryKey;

    public RmsDirectoryProxyController(
            @Value("${cim.iam.base-url:http://localhost:8081}") String iamBaseUrl,
            @Value("${cim.iam.directory-key:dev-directory-key-change-me}") String directoryKey) {
        this.iamBaseUrl = StringUtils.trimTrailingCharacter(iamBaseUrl, '/');
        this.directoryKey = directoryKey;
        this.restTemplate = new RestTemplate();
    }

    /** 组织树（全量；前端建树）。 */
    @GetMapping("/orgs")
    @PreAuthorize("hasAuthority('" + RmsPermissions.ORG_VIEW + "')")
    public Result<List<Map<String, Object>>> orgs(
            @RequestParam(name = "since", required = false) String since) {
        return Result.ok(exchangeList("/api/v1/directory/orgs", since == null ? Map.of() : Map.of("since", since)));
    }

    /** 组织成员用户 ID（缺省含子组织——与 IAM 目录语义一致）。 */
    @GetMapping("/orgs/{orgId}/users")
    @PreAuthorize("hasAuthority('" + RmsPermissions.ORG_VIEW + "')")
    public Result<List<String>> orgMembers(@PathVariable String orgId) {
        ResponseEntity<Result<List<String>>> resp = restTemplate.exchange(
                iamBaseUrl + "/api/v1/directory/orgs/" + orgId + "/users",
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                new ParameterizedTypeReference<>() {
                });
        return resp.getBody() == null ? Result.ok(List.of()) : resp.getBody();
    }

    /** 全量人员档案（映射为前端 AdminUserRow 行形状；无 username 时以 userId 补位）。 */
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('" + RmsPermissions.USER_VIEW + "')")
    public Result<List<DirectoryUserRow>> users() {
        List<Map<String, Object>> profiles = exchangeList("/api/v1/directory/profiles", Map.of());
        return Result.ok(profiles.stream().map(RmsDirectoryProxyController::toUserRow).toList());
    }

    // ------------------------------------------------------------------ 内部

    /** 带服务身份头的目录 API GET；空结果统一为空列表。 */
    private List<Map<String, Object>> exchangeList(String path, Map<String, String> query) {
        StringBuilder url = new StringBuilder(iamBaseUrl + path);
        if (!query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : query.entrySet()) {
                if (!first) url.append('&');
                url.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
        }
        ResponseEntity<Result<List<Map<String, Object>>>> resp = restTemplate.exchange(
                url.toString(),
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                new ParameterizedTypeReference<>() {
                });
        List<Map<String, Object>> data = resp.getBody() == null ? null : resp.getBody().data();
        return data == null ? List.of() : data;
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Directory-Key", directoryKey);
        h.set("Accept", "application/json");
        return h;
    }

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
