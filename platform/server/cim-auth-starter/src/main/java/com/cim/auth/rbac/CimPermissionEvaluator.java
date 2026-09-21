package com.cim.auth.rbac;

import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serializable;
import java.util.Collection;

/**
 * 业务权限评估器（design.md §2.4）：支持 {@code @PreAuthorize("hasPermission('module:res:action')")}。
 *
 * <p>超管（权限含 {@code SUPER_ADMIN}/{@code ROLE_SUPER}）短路放通；
 * 否则按主体权限集匹配权限码。与 {@code hasAuthority(...)} 互补，统一走本评估器。</p>
 */
public class CimPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object target, Object permission) {
        return check(authentication, permission);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        return check(authentication, permission);
    }

    private boolean check(Authentication authentication, Object permission) {
        if (permission == null || authentication == null) {
            return false;
        }
        String perm = permission.toString();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        for (GrantedAuthority ga : authorities) {
            String a = ga.getAuthority();
            if ("SUPER_ADMIN".equals(a) || "ROLE_SUPER".equals(a)) {
                return true;
            }
        }
        for (GrantedAuthority ga : authorities) {
            if (perm.equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
