package com.cim.approval.server.security;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.auth.token.TokenClaims;
import com.cim.spring.support.web.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G3 业务域隔离的解析规则。
 *
 * <p>这里只测「域从哪来、能不能用」，不测具体查询：五类查询（定义/实例/任务/审计/回调）的域条件
 * 全部经由 {@link SecurityGuard#resolveDomain} / {@link SecurityGuard#resolveDomainFilter} 取，
 * 规则对了，查询层只是把返回值拼进条件。</p>
 *
 * <p>三条最贵的回归点：</p>
 * <ol>
 *   <li>令牌 {@code apps} 与流程租户是两个命名空间，跨域判定必须按域集合做，不能拿 tenantId 直接比；</li>
 *   <li>{@code appCode} 是可选参数，但「不传」**不能**意味着「不限域」，否则知道 businessKey 就能读跨域审计；</li>
 *   <li>共享域 {@code default} 必须恒放行，否则升级当天存量数据全线 403。</li>
 * </ol>
 */
class SecurityGuardDomainScopeTest {

    private final CurrentUserResolver currentUser = new CurrentUserResolver();
    private final SecurityGuard guard = new SecurityGuard(currentUser);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestScopeHolder.clear();
    }

    // ---------------------------------------------------------------- 认证态

    @Test
    @DisplayName("认证态：请求域不在授权范围内 → 403")
    void rejectsForeignDomain() {
        authOn(true);
        authenticateAs("approval-ap:ADMIN", SecurityGuard.SHARED_DOMAIN);

        assertThatThrownBy(() -> guard.resolveDomain("rms-ap"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("跨业务域访问被拒绝");
        assertThatThrownBy(() -> guard.resolveDomainFilter("rms-ap"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("认证态：本域与共享域放行，resolveDomain 原样返回")
    void allowsOwnAndSharedDomain() {
        authOn(true);
        authenticateAs("approval-ap:ADMIN", SecurityGuard.SHARED_DOMAIN);

        assertThat(guard.resolveDomain("approval-ap")).isEqualTo("approval-ap");
        assertThat(guard.resolveDomain(SecurityGuard.SHARED_DOMAIN)).isEqualTo(SecurityGuard.SHARED_DOMAIN);
    }

    @Test
    @DisplayName("认证态：不传域 → 收敛为本人的授权域集合，而非「不限域」")
    void unspecifiedDomainConvergesToAllowed() {
        authOn(true);
        authenticateAs("approval-ap:ADMIN", "t1");

        // 令牌 apps=[approval-ap]、tenantId=t1 → 集合含三者，且绝不出现 "*"
        assertThat(guard.resolveDomainFilter(null))
                .containsExactlyInAnyOrder("approval-ap", "t1", SecurityGuard.SHARED_DOMAIN)
                .doesNotContain("*");
        // 单值场景永不返回 null（null 会退化成分页查询「不限域」）
        assertThat(guard.resolveDomain(null)).isEqualTo(SecurityGuard.SHARED_DOMAIN);
    }

    @Test
    @DisplayName("认证态：超管跨域 → \"*\"")
    void superAdminSeesEverything() {
        authOn(true);
        authenticateAs("SUPER_ADMIN", SecurityGuard.SHARED_DOMAIN);

        assertThat(guard.resolveDomainFilter(null)).containsExactly("*");
        assertThat(guard.resolveDomain("rms-ap")).isEqualTo("rms-ap");
    }

    @Test
    @DisplayName("认证态：三段式权限码不会被误判成域，共享域仍在（不会把自己锁死）")
    void permissionCodesAreNotDomains() {
        authOn(true);
        // approval:definition:manage 三段式：若按首个冒号切，会得到域 "approval"
        authenticateAs("approval:definition:manage", SecurityGuard.SHARED_DOMAIN);

        assertThat(guard.resolveDomainFilter(null)).containsExactly(SecurityGuard.SHARED_DOMAIN);
        assertThatThrownBy(() -> guard.resolveDomain("approval")).isInstanceOf(BizException.class);
    }

    // ---------------------------------------------------------------- 开放 API

    @Test
    @DisplayName("开放 API：绑定域放行、他域 403，忽略查询参数自报的域")
    void openApiConvergesToBoundDomain() {
        authOn(true);
        RequestScopeHolder.set(RequestScope.openApi("rms-ap"));

        assertThat(guard.resolveDomainFilter(null)).containsExactly("rms-ap");
        assertThat(guard.resolveDomain(null)).isEqualTo("rms-ap");
        assertThat(guard.resolveDomainFilter("rms-ap")).containsExactly("rms-ap");
        assertThatThrownBy(() -> guard.resolveDomainFilter("mds-ap"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("跨业务域访问被拒绝");
    }

    // ---------------------------------------------------------------- dev

    @Test
    @DisplayName("dev 放行态：不限域，且不改写请求值（保留既有 E2E 行为）")
    void devModeIsUnrestricted() {
        authOn(false); // cim.auth.enabled=false

        assertThat(guard.resolveDomainFilter(null)).containsExactly("*");
        assertThat(guard.resolveDomain("g6-domain")).isEqualTo("g6-domain");
    }

    // ---------------------------------------------------------------- helpers

    private void authOn(boolean enabled) {
        ReflectionTestUtils.setField(currentUser, "authEnabled", enabled);
    }

    private void authenticateAs(String authority, String tenantId) {
        Set<String> authorities = Set.of(authority);
        TokenClaims claims = new TokenClaims("u-1", "测试用户", Set.of("approval-ap"), Set.of("ADMIN"),
                tenantId, "jti-test", authorities, 1L, Instant.now().plusSeconds(600), Map.of());
        CimUserPrincipal principal = new CimUserPrincipal(claims, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
