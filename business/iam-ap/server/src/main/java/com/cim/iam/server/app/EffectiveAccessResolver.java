package com.cim.iam.server.app;

import com.cim.iam.server.org.OrgAppAssignment;
import com.cim.iam.server.org.OrgGrantService;
import com.cim.iam.server.org.OrgNode;
import com.cim.iam.server.org.OrgNodeRepository;
import com.cim.iam.server.org.UserOrg;
import com.cim.iam.server.org.UserOrgRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 有效准入解析（identity-directory.md §4）。
 *
 * <p><b>运行时展开，不落派生行</b>（避免「组织改了但派生行没同步」的不一致）：</p>
 * <pre>
 * effectiveApps(uid)      = 个人授予(uid)  ∪  组织授予(uid 的全部归属组织及其祖先链)
 * effectiveRoles(uid,app) =                       上述来源的 roles 并集
 * </pre>
 *
 * <p>祖先链靠 {@link OrgNode#getPath()} 判定：组织授予 G 对用户生效 ⟺
 * 用户归属组织 = G.orgId，或（{@code includeChildren} 且用户归属组织的绝对路径以 G 组织的路径为前缀）。</p>
 *
 * <p>一致性：组织/归属/组织授予任一变更 → 变更方已 bump 受影响用户 tokenVersion →
 * 旧令牌 401 → 重签即带新准入，无需额外机制。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectiveAccessResolver {

    private final AppRegistrationService appRegistrationService;
    private final AppRegistrationRepository appRepo;
    private final OrgGrantService orgGrantService;
    private final OrgNodeRepository orgNodeRepository;
    private final UserOrgRepository userOrgRepository;

    /** 有效准入 ap 集合（个人 ∪ 组织）。 */
    public Set<String> effectiveApps(String userId) {
        Set<String> apps = new LinkedHashSet<>(appRegistrationService.enabledAppsForUser(userId));
        apps.addAll(orgGrantRoles(userId).keySet());
        return apps;
    }

    /** 有效角色（按 ap 分组，个人 ∪ 组织取并集）。 */
    public Map<String, Set<String>> effectiveRoles(String userId) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        appRegistrationService.rolesByAppForUser(userId)
                .forEach((app, roles) -> map.computeIfAbsent(app, k -> new LinkedHashSet<>()).addAll(roles));
        orgGrantRoles(userId)
                .forEach((app, roles) -> map.computeIfAbsent(app, k -> new LinkedHashSet<>()).addAll(roles));
        return map;
    }

    /** 组织授予在当前用户上展开出的 { ap → roles }（已过滤 app 未启用的授予）。 */
    private Map<String, Set<String>> orgGrantRoles(String userId) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        List<UserOrg> memberships = userOrgRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return result;
        }
        Set<String> userOrgIds = new LinkedHashSet<>();
        for (UserOrg uo : memberships) {
            userOrgIds.add(uo.getOrgId());
        }
        List<OrgNode> userOrgs = orgNodeRepository.findAllById(userOrgIds);

        List<OrgAppAssignment> grants = orgGrantService.listEnabled();
        if (grants.isEmpty()) {
            return result;
        }
        Set<String> grantOrgIds = new LinkedHashSet<>();
        for (OrgAppAssignment g : grants) {
            grantOrgIds.add(g.getOrgId());
        }
        Map<String, OrgNode> grantOrgs = new LinkedHashMap<>();
        for (OrgNode n : orgNodeRepository.findAllById(grantOrgIds)) {
            grantOrgs.put(n.getId(), n);
        }

        for (OrgAppAssignment g : grants) {
            OrgNode gOrg = grantOrgs.get(g.getOrgId());
            if (gOrg == null) {
                continue;
            }
            if (!appliesTo(g, gOrg, userOrgs) || !isAppEnabled(g.getAppCode())) {
                continue;
            }
            result.computeIfAbsent(g.getAppCode(), k -> new LinkedHashSet<>()).addAll(roleSet(g.getRoles()));
        }
        return result;
    }

    /**
     * 授予 G 是否作用于用户的某个归属组织 O。
     *
     * <p>{@code O.path} 为<b>从根算起的绝对路径</b>（{@code /rootId/.../selfId/}），
     * 因此「G 是 O 的祖先」⟺ {@code O.path.startsWith(G.path)}。</p>
     */
    private static boolean appliesTo(OrgAppAssignment g, OrgNode grantedOrg, List<OrgNode> userOrgs) {
        for (OrgNode o : userOrgs) {
            if (o.getId().equals(g.getOrgId())) {
                return true;
            }
            if (g.isIncludeChildren() && o.getPath() != null && grantedOrg.getPath() != null
                    && o.getPath().startsWith(grantedOrg.getPath())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAppEnabled(String appCode) {
        return appRepo.findByAppCode(appCode)
                .map(a -> a.getStatus() == AppStatus.ENABLED)
                .orElse(false);
    }

    private static Set<String> roleSet(String roles) {
        Set<String> set = new LinkedHashSet<>();
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
