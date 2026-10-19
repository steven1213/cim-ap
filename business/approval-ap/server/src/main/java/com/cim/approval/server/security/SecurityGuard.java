package com.cim.approval.server.security;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 统一安全守卫（G1 权限码三层 + G3 业务域隔离）。
 *
 * <h3>业务域作用域（G3）</h3>
 * <ul>
 *   <li>开放 API（X-App-Key 鉴权通过）：仅该 appKey 绑定的单一业务域，越域访问 403；</li>
 *   <li>JWT 认证态：由令牌 {@code apps} 权威推导（"*" 为超管跨域），缺域则拒绝；</li>
 *   <li>dev 放行态：信任全部（"*"），便于无 IAM 联调。</li>
 * </ul>
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
     */
    public List<String> resolveAllowedAppCodes() {
        RequestScope scope = RequestScopeHolder.get();
        if (scope != null && scope.viaOpenApi()) {
            return scope.allowedAppCodes();
        }
        if (currentUser.authEnabled()) {
            return deriveFromAuthentication();
        }
        return List.of("*");
    }

    private List<String> deriveFromAuthentication() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) {
            return List.of(); // 认证态无身份 → 拒绝具体域
        }
        Collection<? extends GrantedAuthority> auths = a.getAuthorities();
        boolean superAdmin = auths.stream()
                .anyMatch(g -> ApprovalPermissions.ROLE_SUPER_ADMIN.equals(g.getAuthority()));
        if (superAdmin) {
            return List.of("*");
        }
        List<String> codes = new ArrayList<>();
        for (GrantedAuthority g : auths) {
            String au = g.getAuthority();
            if (au != null && au.startsWith("app:")) {
                codes.add(au.substring(4));
            }
        }
        return codes;
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
                return a.getAuthorities().stream()
                        .anyMatch(g -> ApprovalPermissions.ROLE_SUPER_ADMIN.equals(g.getAuthority()));
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
     * @param requested 请求指定的业务域（通常即 tenantId / appCode）；null 表示不限定，放行
     */
    public void assertAppCodeScope(String requested) {
        if (requested == null || requested.isBlank()) {
            return;
        }
        List<String> allowed = resolveAllowedAppCodes();
        if (allowed.contains("*") || allowed.contains(requested)) {
            return;
        }
        throw new BizException(BizCode.ACCESS_DENIED,
                "跨业务域访问被拒绝：请求域 [" + requested + "] 不在授权范围内");
    }

    /**
     * 平台权限码断言（G1）。认证态按 Spring Security 权威校验（超管跨域放行）；
     * dev 态由 dev-bypass / dev-enforce 控制。
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
            if (a != null && a.getAuthorities().stream()
                    .anyMatch(g -> code.equals(g.getAuthority()))) {
                return;
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
