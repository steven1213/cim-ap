package com.cim.approval.server.security;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一安全守卫（G1 权限码三层 + G3 业务域隔离）。
 *
 * <h3>业务域作用域（G3）</h3>
 *
 * <p><b>域 = appCode</b>。域不是「租户」：租户（{@code tenantId}）是流程变量，一个租户下可以有多个
 * 业务方；域是「哪个 ap 的数据」，与令牌 {@code apps} 同一命名空间。二者在单租户部署里取值相同
 * （都是 {@code default}），所以极易混为一谈——历史上那 6 处 {@code assertAppCodeScope(tenantId)}
 * 就是这么来的。</p>
 *
 * <ul>
 *   <li>开放 API（X-App-Key 鉴权通过）：仅该 appKey 绑定的单一业务域，越域访问 403；</li>
 *   <li>JWT 认证态：{@code 令牌 apps} ∪ {@code 令牌 tenantId} ∪ {@link #SHARED_DOMAIN}；</li>
 *   <li>dev 放行态：信任全部（"*"），便于无 IAM 联调。</li>
 * </ul>
 *
 * <p><b>{@link #SHARED_DOMAIN}（default）恒在允许集合内</b>：未按业务方分域的历史数据全落在这里，
 * 若不放行，升级当天所有存量单据与列表会集体消失。代价是该域对所有已准入的 ap 可读——这是显式的
 * 共享域妥协，不是漏洞；真正的隔离从「新实例按各自的 appCode 写入」开始生效。</p>
 *
 * <h3>权限码三层（G1 / D6）</h3>
 * <ol>
 *   <li>平台超管（IAM {@code is_super} → 权威 {@link ApprovalPermissions#ROLE_SUPER_ADMIN}）：跨域、可所有管理端点；</li>
 *   <li>域管理员：持有管理权限码且作用域含目标 appCode；</li>
 *   <li>定义管理者：管理权限码 + 定义级归属（后续按定义元数据收敛，本期以域管理员为基准）。</li>
 * </ol>
 *
 * <p>dev 态由 {@code approval.security.dev-bypass}（默认 true，放行）与
 * {@code approval.security.dev-enforce}（开启后按 {@code dev-super-admin} 白名单 deny 非超管）双开关控制，
 * 便于在本地复现 deny 路径而不影响既有 E2E。</p>
 */
@Component
public class SecurityGuard {

    private final CurrentUserResolver currentUser;

    /**
     * 平台超管权威的几种写法。
     *
     * <p>平台约定（{@code CimUserPrincipal#isSuper} / {@code CimPermissionEvaluator}）认
     * {@code SUPER_ADMIN} 与 {@code ROLE_SUPER}；本类历史上按 {@code ROLE_SUPER_ADMIN} 写。
     * 三者都收，避免「IAM 按平台约定发 {@code SUPER_ADMIN}、审批平台却看不见」。</p>
     */
    private static final Set<String> SUPER_AUTHORITIES =
            Set.of("SUPER_ADMIN", "ROLE_SUPER", "ROLE_SUPER_ADMIN");

    /**
     * 平台共享域。历史数据（未按业务方分域的实例/审计）全部落在该域，所有已准入的 ap 均可读。
     *
     * <p>用常量而非散落的字面量，是因为它同时出现在三处语义里：前端 {@code identityStore} 的
     * 缺省租户、{@code TaskController} 的 {@code defaultValue}、以及这里的允许集合。任一处改字面量
     * 都会造成「登录成功但一条待办都看不到」这种不报错的故障。</p>
     */
    public static final String SHARED_DOMAIN = "default";

    @Value("${approval.security.dev-bypass:true}")
    private boolean devBypass;

    @Value("${approval.security.dev-enforce:false}")
    private boolean devEnforce;

    @Value("${approval.security.dev-super-admin:admin}")
    private String devSuperAdminCsv;

    public SecurityGuard(CurrentUserResolver currentUser) {
        this.currentUser = currentUser;
    }

    /** 开放 API 解析出的业务域（仅经 ApiAuthFilter 鉴权后非空）；否则返回 null。 */
    public String resolvedAppCodeOrNull() {
        RequestScope scope = RequestScopeHolder.get();
        return scope != null && scope.viaOpenApi() ? scope.resolvedAppCode() : null;
    }

    /**
     * 解析调用方可访问的业务域集合。开放 API 收敛为单一域；JWT 态由权威推导；
     * dev 态返回 ["*"]（信任）。
     *
     * <p>JWT 态是<b>并集</b>：令牌 {@code apps}（哪些 ap 调得动我）+ 令牌 {@code tenantId}
     * （同租户内视为一方）+ 共享域。只取 {@code apps} 会让「单租户、各 ap 都写 default」的
     * 存量部署全线 403——那正是上一轮 {@code assertAppCodeScope(tenantId)} 的做法。</p>
     */
    public List<String> resolveAllowedAppCodes() {
        RequestScope scope = RequestScopeHolder.get();
        if (scope != null && scope.viaOpenApi()) {
            return scope.allowedAppCodes();
        }
        if (currentUser.authEnabled()) {
            List<String> derived = deriveFromAuthentication();
            if (derived.contains("*")) {
                return derived; // 超管跨域
            }
            Set<String> union = new LinkedHashSet<>(derived);
            String tenant = jwtTenantOrNull();
            if (tenant != null && !tenant.isBlank()) {
                union.add(tenant);
            }
            union.add(SHARED_DOMAIN);
            return List.copyOf(union);
        }
        return List.of("*");
    }

    /**
     * 解析「本次请求可查询的业务域集合」——<b>五类查询（定义/实例/任务/审计/回调）唯一的域来源</b>。
     *
     * <p>规则：请求域必须落在允许集合内（越域 403，不是 404，避免泄漏存在性）；未指定时
     * <b>收敛为调用方全部授权域</b>，而不是放开全量——这正是原实现的洞：{@code appCode} 是可选参数，
     * 不传就走「不限域」分支，知道 businessKey 就能读到跨域审计。</p>
     *
     * @param requested 请求指定的域；null/blank 表示未指定
     * @return 可查询的域集合；含 {@code "*"} 表示不限（超管 / dev 放行态）
     */
    public List<String> resolveDomainFilter(String requested) {
        RequestScope scope = RequestScopeHolder.get();
        if (scope != null && scope.viaOpenApi()) {
            // 开放 API：忽略调用方传入的域，强制收敛为 appKey 绑定域
            String bound = scope.resolvedAppCode();
            if (requested != null && !requested.isBlank() && !requested.equals(bound)) {
                throw crossDomain(requested, List.of(bound));
            }
            return List.of(bound);
        }
        List<String> allowed = resolveAllowedAppCodes();
        if (allowed.contains("*")) {
            return (requested == null || requested.isBlank()) ? List.of("*") : List.of(requested);
        }
        if (requested == null || requested.isBlank()) {
            return allowed;
        }
        if (!allowed.contains(requested)) {
            throw crossDomain(requested, allowed);
        }
        return List.of(requested);
    }

    /**
     * 单值域解析：写操作与「按单个域查询」的域条件来源。
     *
     * <p>返回 null 会退化成「不限域」，所以这里永不返回 null：未指定时取
     * {@link #SHARED_DOMAIN}（可读且不会误放开全量），开放 API 下取 appKey 绑定域。</p>
     */
    public String resolveDomain(String requested) {
        RequestScope scope = RequestScopeHolder.get();
        if (scope != null && scope.viaOpenApi()) {
            return resolveDomainFilter(requested).get(0); // 恒为该 appKey 绑定域
        }
        if (requested == null || requested.isBlank()) {
            return SHARED_DOMAIN;
        }
        List<String> filter = resolveDomainFilter(requested);
        return filter.contains("*") ? requested : filter.get(0);
    }

    private static BizException crossDomain(String requested, List<String> allowed) {
        return new BizException(BizCode.ACCESS_DENIED,
                "跨业务域访问被拒绝：请求域 [" + requested + "] 不在授权范围 " + allowed + " 内");
    }

    /** 令牌里的租户（{@code CimUserPrincipal#tenantId}）；无身份时返回 null。 */
    private String jwtTenantOrNull() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof com.cim.auth.principal.CimUserPrincipal p) {
            return p.tenantId();
        }
        return null;
    }

    /**
     * 从 Spring Security 权威推导可访问的业务域。
     *
     * <p><b>IAM 实际下发的形态是 {@code <appCode>:<ROLE>}</b>（见 IAM {@code TokenIssuerService}：
     * {@code roles} 与 {@code authorities} 两个 claim 同值，如 {@code approval-ap:ADMIN}）。
     * 本方法原先只认 {@code app:<appCode>} 前缀——那个前缀**全仓无人产生**，于是认证态下
     * 域集合恒为空、所有带域判定的查询一律 403。这里两种形态都认：</p>
     *
     * <ul>
     *   <li>{@code app:<appCode>}：历史/预留写法，保留兼容；</li>
     *   <li>{@code <appCode>:<ROLE>}：IAM 现行写法。仅当「恰好两段」且首段形如 {@code *-ap}
     *       时才取首段——否则会把三段式权限码 {@code approval:definition:manage} 误判成域
     *       {@code approval}。</li>
     * </ul>
     */
    private List<String> deriveFromAuthentication() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) {
            return List.of(); // 认证态无身份 → 拒绝具体域
        }
        Collection<? extends GrantedAuthority> auths = a.getAuthorities();
        if (auths.stream().anyMatch(g -> isSuperAuthority(g.getAuthority()))) {
            return List.of("*");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (GrantedAuthority g : auths) {
            String au = g.getAuthority();
            if (au == null || au.isBlank()) {
                continue;
            }
            if (au.startsWith("app:")) {
                codes.add(au.substring("app:".length()));
                continue;
            }
            int first = au.indexOf(':');
            boolean exactlyTwoSegments = first > 0 && au.indexOf(':', first + 1) < 0;
            if (exactlyTwoSegments) {
                String head = au.substring(0, first);
                if (head.endsWith("-ap")) {
                    codes.add(head);
                }
            }
        }
        return List.copyOf(codes);
    }

    private static boolean isSuperAuthority(String authority) {
        return authority != null && SUPER_AUTHORITIES.contains(authority);
    }

    /** 是否平台超管（跨域）。开放 API 恒 false；JWT 看权威；dev 看 {@code dev-super-admin} 白名单。 */
    public boolean isSuperAdmin() {
        RequestScope scope = RequestScopeHolder.get();
        if (scope != null && scope.viaOpenApi()) {
            return false;
        }
        if (currentUser.authEnabled()) {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null) {
                return a.getAuthorities().stream().anyMatch(g -> isSuperAuthority(g.getAuthority()));
            }
            return false;
        }
        return isDevSuperAdmin(currentUser.resolveNullable(null));
    }

    private boolean isDevSuperAdmin(String actor) {
        if (actor == null) {
            return false;
        }
        for (String s : devSuperAdminCsv.split(",")) {
            if (s.trim().equals(actor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 业务域隔离断言（G3）。请求的业务域须落在调用方作用域内，否则 403（不返回 404，避免泄漏存在性）。
     *
     * <p>这是 {@link #resolveDomainFilter(String)} 的判定形态，供「只校验不取域」的场合使用
     * （如按 id 打开某个资源时校验该资源归属域）。null 表示不限定，放行。</p>
     *
     * @param requested 资源或请求指定的业务域；null 表示全局共享资源
     */
    public void assertAppCodeScope(String requested) {
        if (requested == null || requested.isBlank()) {
            return;
        }
        resolveDomainFilter(requested); // 越域时抛 403
    }

    /**
     * 平台权限码断言（G1）。认证态按 Spring Security 权威校验（超管跨域放行）；
     * dev 态由 dev-bypass / dev-enforce 控制。
     *
     * <p><b>域管理员通路</b>：G1 第 2 层定义的「域管理员」在本平台就是某 ap 的 {@code ADMIN}
     * 角色（IAM 下发的 {@code <appCode>:ADMIN}）。IAM 目前只授角色组、不授细粒度权限码，
     * 若这里只做权限码精确匹配，域管理员会被挡在所有管理端点外（表现为「登录成功但点哪都 403」）。
     * 故本 ap 的 {@code ADMIN} 视为持有全部 {@code approval:*} 权限码；待 IAM 支持
     * 「应用角色 → 权限集」后应收敛为按码判定。</p>
     *
     * @param code       所需权限码（见 {@link ApprovalPermissions}）
     * @param actorParam 调用方身份（dev 态用于 dev-super-admin 白名单判定；可为 null）
     */
    public void assertPermission(String code, String actorParam) {
        if (currentUser.authEnabled()) {
            if (isSuperAdmin()) {
                return;
            }
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null) {
                boolean granted = a.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(au -> code.equals(au)
                                || ApprovalPermissions.DOMAIN_ADMIN_AUTHORITY.equals(au));
                if (granted) {
                    return;
                }
            }
            throw new BizException(BizCode.ACCESS_DENIED, "缺少权限码：" + code);
        }
        // dev 态
        if (devEnforce) {
            if (!isDevSuperAdmin(currentUser.resolveNullable(actorParam))) {
                throw new BizException(BizCode.ACCESS_DENIED, "缺少权限码（dev-enforce）：" + code);
            }
            return;
        }
        // dev-bypass（默认）：放行
    }
}
