package com.cim.auth.support;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.core.port.CurrentUserPort;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * 当前操作人端口的认证侧实现（design.md §2.4 / T3.7）：从 {@code SecurityContext} 读出
 * {@link CimUserPrincipal}，供 {@code cim-jpa-starter} 审计填充（create_user/event_user）。
 *
 * <p>以 {@code @ConditionalOnMissingBean(CurrentUserPort.class)} 注册，覆盖
 * cim-spring-support 的匿名实现（见 SecurityAutoConfiguration 的 @AutoConfigureBefore）。</p>
 */
public class SecurityContextPortAdapter implements CurrentUserPort {

    @Override
    public String userId() {
        CimUserPrincipal p = principal();
        return p == null ? null : p.userId();
    }

    @Override
    public String username() {
        CimUserPrincipal p = principal();
        return p == null ? null : p.username();
    }

    @Override
    public Set<String> authorities() {
        CimUserPrincipal p = principal();
        return p == null ? Set.of() : p.authoritiesSet();
    }

    @Override
    public boolean isSuper() {
        CimUserPrincipal p = principal();
        return p != null && p.isSuper();
    }

    private CimUserPrincipal principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof CimUserPrincipal p) {
            return p;
        }
        return null;
    }
}
