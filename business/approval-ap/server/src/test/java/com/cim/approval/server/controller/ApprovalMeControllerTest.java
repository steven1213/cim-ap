package com.cim.approval.server.controller;

import com.cim.auth.principal.CimUserPrincipal;
import com.cim.auth.token.TokenClaims;
import com.cim.system.rbac.LocalUserResolver;
import com.cim.system.rbac.SysRbacService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApprovalMeController} 的身份映射。
 *
 * <p>只测「principal → DTO」这一段与认证开关的透传：前端的通道判定（dev 还是统一登录）
 * 完全建立在这两个值上，映射错了会表现成「登录成功但看不到自己的待办」，且不报错。</p>
 */
class ApprovalMeControllerTest {

    private final LocalUserResolver userResolver = Mockito.mock(LocalUserResolver.class);
    private final SysRbacService rbacService = Mockito.mock(SysRbacService.class);
    private final ApprovalMeController controller = new ApprovalMeController(userResolver, rbacService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        Mockito.reset(userResolver, rbacService);
    }

    @Test
    @DisplayName("认证态：档案取自 principal，开关透传 true")
    void mapsPrincipalClaims() {
        ReflectionTestUtils.setField(controller, "authEnabled", true);
        authenticateAs(new CimUserPrincipal(
                claims("u-1001", "张三", "t1", Set.of("manager"), Set.of("approval:audit:view")),
                Set.of("approval:audit:view")));

        ApprovalMeController.MeDto dto = controller.me().data();

        assertThat(dto.userId()).isEqualTo("u-1001");
        assertThat(dto.username()).isEqualTo("张三");
        assertThat(dto.tenantId()).isEqualTo("t1");
        assertThat(dto.roles()).containsExactly("manager");
        assertThat(dto.permissions()).containsExactly("approval:audit:view");
        assertThat(dto.authEnabled()).isTrue();
    }

    @Test
    @DisplayName("dev 态：无 principal 时只回开关 false，身份字段为空")
    void reportsDevModeWithoutPrincipal() {
        ReflectionTestUtils.setField(controller, "authEnabled", false);

        ApprovalMeController.MeDto dto = controller.me().data();

        assertThat(dto.userId()).isNull();
        assertThat(dto.roles()).isEmpty();
        assertThat(dto.permissions()).isEmpty();
        // 前端据此保留身份切换器、继续用 userId/tenantId 查询参数
        assertThat(dto.authEnabled()).isFalse();
    }

    @Test
    @DisplayName("认证态菜单：按 sys_role_menu 过滤（menuTree，不含 BUTTON）")
    void ssoMenusFilteredByRole() {
        authenticateAs(new CimUserPrincipal(
                claims("u-1001", "张三", "t1", Set.of("manager"), Set.of()),
                Set.of()));
        com.cim.system.user.SysUser user = Mockito.mock(com.cim.system.user.SysUser.class);
        Mockito.when(user.getId()).thenReturn("local-1");
        Mockito.when(userResolver.resolve("u-1001", "张三")).thenReturn(java.util.Optional.of(user));
        Mockito.when(rbacService.menuTree("local-1", false)).thenReturn(List.of());

        assertThat(controller.myMenus().data()).isEmpty();
        Mockito.verify(rbacService).menuTree("local-1", false);
    }

    @Test
    @DisplayName("dev 态菜单：无 principal 时回全量树（演示通道不做角色过滤）")
    void devMenusReturnFullTree() {
        Mockito.when(rbacService.fullMenuTree(false)).thenReturn(List.of());

        assertThat(controller.myMenus().data()).isEmpty();
        Mockito.verify(rbacService).fullMenuTree(false);
        Mockito.verifyNoInteractions(userResolver);
    }

    private static void authenticateAs(CimUserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static TokenClaims claims(String userId, String username, String tenantId,
                                      Set<String> roles, Set<String> authorities) {
        return new TokenClaims(userId, username, Set.of("approval-ap"), roles, tenantId,
                "jti-test", authorities, 1L, Instant.now().plusSeconds(600), Map.of());
    }
}
