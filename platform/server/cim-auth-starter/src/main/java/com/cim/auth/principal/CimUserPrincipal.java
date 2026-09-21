package com.cim.auth.principal;

import com.cim.auth.token.TokenClaims;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证主体（design.md §2.4）：承载用户/权限集/租户，供审计填充与数据权限取用。
 *
 * <p>实现 {@link UserDetails} 以便直接作为 Spring Security 的 Authentication 主体，
 * 其 {@code getAuthorities()} 来自本 ap 的 RBAC 权限集（由 {@code LocalAuthorityLoader} 加载）。</p>
 */
public class CimUserPrincipal implements UserDetails {

    private final TokenClaims claims;
    private final Set<String> authorities;
    private final Collection<GrantedAuthority> granted;

    public CimUserPrincipal(TokenClaims claims, Set<String> authorities) {
        this.claims = claims;
        this.authorities = authorities == null ? Set.of() : authorities;
        this.granted = this.authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    public String userId() {
        return claims.userId();
    }

    public String username() {
        return claims.username();
    }

    public String tenantId() {
        return claims.tenantId();
    }

    public Set<String> roles() {
        return claims.roles();
    }

    public Set<String> authoritiesSet() {
        return authorities;
    }

    public boolean isSuper() {
        return authorities.contains("SUPER_ADMIN") || authorities.contains("ROLE_SUPER");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return granted;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return claims.userId();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
