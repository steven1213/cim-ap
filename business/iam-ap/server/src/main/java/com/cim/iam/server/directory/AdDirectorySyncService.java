package com.cim.iam.server.directory;

import com.cim.iam.server.audit.AuditService;
import com.cim.iam.server.audit.AuditType;
import com.cim.iam.server.common.DataOrigin;
import com.cim.iam.server.org.OrgNodeRepository;
import com.cim.iam.server.org.OrgNodeService;
import com.cim.iam.server.org.OrgNodeType;
import com.cim.iam.server.profile.ProfileService;
import com.cim.iam.server.profile.UserStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

/**
 * AD / LDAP 只读目录同步器（identity-directory.md §6 / §8 T0-7）。
 *
 * <p><b>未配置即跳过</b>：{@link AdDirectorySyncProperties#isConfigured()} 为假时不连接、不报错、
 * 不阻塞启动——纯 IAM 自建目录是默认可用态（已与用户确认）。</p>
 *
 * <p><b>只写自己那一份</b>：组织与人只写 {@link DataOrigin#AD_SYNCED} 行，绝不覆盖 IAM 自建节点；
 * AD 侧删除 → IAM 侧置 INACTIVE（不物理删除，保留审计与准入历史）。</p>
 *
 * <p>沿用 JNDI（不引入新依赖），用 {@link PagedResultsControl} 分页检索。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdDirectorySyncService {

    private static final String ATTR_DN = "distinguishedName";
    private static final String ATTR_SAM = "sAMAccountName";
    private static final String ATTR_EMP = "employeeID";
    private static final String ATTR_DISPLAY = "displayName";
    private static final String ATTR_MAIL = "mail";
    private static final String ATTR_MOBILE = "mobile";
    private static final String ATTR_UAC = "userAccountControl";

    /** ACCOUNTDISABLE 位（userAccountControl）。 */
    private static final int UAC_ACCOUNTDISABLE = 0x0002;

    private final AdDirectorySyncProperties props;
    private final OrgNodeService orgNodeService;
    private final OrgNodeRepository orgNodeRepository;
    private final ProfileService profileService;
    private final AuditService auditService;

    /** 同步结果（{@code skipped=true} 表示未配置 AD，属正常态非错误）。 */
    public record SyncResult(boolean skipped, String reason, int orgCount, int userCount, int deactivated) {
    }

    /** 定时同步：任何异常都不得影响服务运行。 */
    @Scheduled(fixedDelayString = "${cim.iam.directory.ad.sync-interval-ms:300000}")
    public void scheduledSync() {
        try {
            SyncResult r = syncNow();
            if (!r.skipped()) {
                log.info("[ad-sync] 定时同步完成 orgs={} users={} deactivated={}",
                        r.orgCount(), r.userCount(), r.deactivated());
            }
        } catch (Exception e) {
            log.warn("[ad-sync] 定时同步异常（不影响服务）：{}", e.getMessage());
        }
    }

    /** 手动/定时触发一次同步。 */
    public SyncResult syncNow() {
        if (!props.isConfigured()) {
            log.info("[ad-sync] 未配置 AD，跳过同步（IAM 自建目录照常可用）");
            return new SyncResult(true,
                    "未配置 AD（cim.iam.directory.ad.enabled / url / base-dn 缺省），已跳过；纯 IAM 自建目录照常可用",
                    0, 0, 0);
        }
        InitialLdapContext ctx = null;
        try {
            ctx = connect();
            int orgCount = syncOrgs(ctx);
            Set<String> seen = new HashSet<>();
            int userCount = syncUsers(ctx, seen);
            int deactivated = profileService.deactivateMissingAdProfiles(seen);
            auditService.success(AuditType.DIRECTORY_SYNCED, null, "AD",
                    "AD 同步完成：组织 " + orgCount + "、人员 " + userCount + "、停用 " + deactivated);
            return new SyncResult(false, null, orgCount, userCount, deactivated);
        } catch (NamingException e) {
            log.warn("[ad-sync] AD 不可达或检索失败：{}", e.getMessage());
            auditService.failure(AuditType.DIRECTORY_SYNCED, null, "AD", "AD 同步失败：" + e.getMessage());
            return new SyncResult(false, "AD 同步失败：" + e.getMessage(), 0, 0, 0);
        } finally {
            closeQuietly(ctx);
        }
    }

    // ---------- 内部实现 ----------

    private InitialLdapContext connect() throws NamingException {
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, props.getUrl());
        env.put("java.naming.referral", "ignore");
        if (props.getBindDn() != null && !props.getBindDn().isBlank()) {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, props.getBindDn());
            env.put(Context.SECURITY_CREDENTIALS, props.getBindPassword());
        }
        return new InitialLdapContext(env, null);
    }

    /** 同步组织（OU）：由浅到深保证父先落地。 */
    private int syncOrgs(InitialLdapContext ctx) throws NamingException {
        List<String> dns = new ArrayList<>();
        for (SearchResult sr : searchAll(ctx, props.getBaseDn(), props.getOrgFilter(),
                new String[]{ATTR_DN, "name"})) {
            String dn = firstAttr(sr.getAttributes(), ATTR_DN, "name");
            if (dn != null && !dn.isBlank()) {
                dns.add(dn);
            }
        }
        dns.sort(Comparator.comparingInt(AdDirectorySyncService::dnDepth));
        int n = 0;
        for (String dn : dns) {
            String name = rdnValue(dn);
            orgNodeService.upsertAdSynced(dn, parentDn(dn), name, name, OrgNodeType.DEPT, 0);
            n++;
        }
        return n;
    }

    /** 同步人员：写档案 + 按 DN 祖先 OU 建归属。 */
    private int syncUsers(InitialLdapContext ctx, Set<String> seen) throws NamingException {
        int n = 0;
        for (SearchResult sr : searchAll(ctx, props.getBaseDn(), props.getUserFilter(),
                new String[]{ATTR_DN, ATTR_SAM, ATTR_EMP, ATTR_DISPLAY, ATTR_MAIL, ATTR_MOBILE, ATTR_UAC})) {
            Attributes a = sr.getAttributes();
            String sam = firstAttr(a, ATTR_SAM);
            if (sam == null || sam.isBlank()) {
                continue;
            }
            UserStatus status = accountEnabled(a) ? UserStatus.ACTIVE : UserStatus.INACTIVE;
            profileService.upsertAdProfile(sam, firstAttr(a, ATTR_EMP), firstAttr(a, ATTR_DISPLAY),
                    firstAttr(a, ATTR_MAIL), firstAttr(a, ATTR_MOBILE), status);
            seen.add(sam);

            String dn = firstAttr(a, ATTR_DN);
            if (dn != null) {
                for (String ancestor : ancestorDns(dn, props.getBaseDn())) {
                    orgNodeRepository.findBySourceAndExternalId(DataOrigin.AD_SYNCED, ancestor)
                            .ifPresent(o -> profileService.upsertAdMembership(sam, o.getId()));
                }
            }
            n++;
        }
        return n;
    }

    /** JNDI 分页检索（PagedResultsControl）。 */
    private List<SearchResult> searchAll(InitialLdapContext ctx, String base, String filter, String[] attrs)
            throws NamingException {
        List<SearchResult> out = new ArrayList<>();
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        sc.setReturningAttributes(attrs);
        byte[] cookie = null;
        try {
            ctx.setRequestControls(new Control[]{
                    new PagedResultsControl(props.getPageSize(), Control.NONCRITICAL)});
        } catch (IOException e) {
            throw new NamingException("设置分页控制失败: " + e.getMessage());
        }
        do {
            NamingEnumeration<SearchResult> en = ctx.search(base, filter, sc);
            try {
                while (en != null && en.hasMore()) {
                    out.add(en.next());
                }
            } finally {
                if (en != null) {
                    en.close();
                }
            }
            cookie = null;
            Control[] resp = ctx.getResponseControls();
            if (resp != null) {
                for (Control c : resp) {
                    if (c instanceof PagedResultsResponseControl prrc) {
                        cookie = prrc.getCookie();
                    }
                }
            }
            if (cookie != null) {
                try {
                    ctx.setRequestControls(new Control[]{
                            new PagedResultsControl(props.getPageSize(), cookie, Control.NONCRITICAL)});
                } catch (IOException e) {
                    cookie = null;
                }
            }
        } while (cookie != null);
        return out;
    }

    private static boolean accountEnabled(Attributes a) {
        String uac = firstAttr(a, ATTR_UAC);
        if (uac == null) {
            return true;
        }
        try {
            return (Integer.parseInt(uac.trim()) & UAC_ACCOUNTDISABLE) == 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static String firstAttr(Attributes attrs, String... names) {
        if (attrs == null) {
            return null;
        }
        for (String name : names) {
            try {
                Attribute at = attrs.get(name);
                if (at != null && at.size() > 0) {
                    Object v = at.get();
                    if (v != null) {
                        return String.valueOf(v);
                    }
                }
            } catch (NamingException ignored) {
                // 该属性不可读则尝试下一个
            }
        }
        return null;
    }

    /** DN 的父 DN（去掉首个 RDN）；无逗号则返回 null。 */
    private static String parentDn(String dn) {
        if (dn == null) {
            return null;
        }
        int idx = dn.indexOf(',');
        return idx < 0 ? null : dn.substring(idx + 1).trim();
    }

    /** DN 的各层祖先（自下而上，止于 baseDn）。 */
    private static List<String> ancestorDns(String dn, String baseDn) {
        List<String> out = new ArrayList<>();
        String cur = parentDn(dn);
        while (cur != null && !cur.isBlank() && !cur.equalsIgnoreCase(baseDn)) {
            out.add(cur);
            cur = parentDn(cur);
        }
        return out;
    }

    private static int dnDepth(String dn) {
        int n = 0;
        for (int i = 0; i < dn.length(); i++) {
            if (dn.charAt(i) == ',') {
                n++;
            }
        }
        return n;
    }

    /** 取首个 RDN 的值（如 {@code OU=蚀刻一科,DC=corp} → {@code 蚀刻一科}）。 */
    private static String rdnValue(String dn) {
        int comma = dn.indexOf(',');
        String rdn = comma < 0 ? dn : dn.substring(0, comma);
        int eq = rdn.indexOf('=');
        return eq < 0 ? rdn : rdn.substring(eq + 1).trim();
    }

    private static void closeQuietly(InitialLdapContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException ignored) {
                // 关闭失败不影响同步结论
            }
        }
    }
}
