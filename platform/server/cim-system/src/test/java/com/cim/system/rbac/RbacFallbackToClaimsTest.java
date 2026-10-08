package com.cim.system.rbac;

import com.cim.auth.rbac.LocalAuthorityLoader;
import com.cim.auth.token.TokenClaims;
import com.cim.system.TestApplication;
import com.cim.system.permission.SysPermission;
import com.cim.system.permission.SysPermissionRepository;
import com.cim.system.role.SysRole;
import com.cim.system.role.SysRoleRepository;
import com.cim.system.role.SysUserRole;
import com.cim.system.role.SysUserRoleRepository;
import com.cim.system.user.SysUser;
import com.cim.system.user.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 灰度迁移策略验证：{@code cim.system.rbac.fallback-to-claims=true}。
 *
 * <p>该开关的意义是「先把模块接进来、再逐步补授权」——把 cim-system 引入一个已在运行的 ap 时，
 * 若立刻按空授权拒绝，所有人会被锁在门外。开启回退则暂时沿用 IAM 令牌的 {@code authorities}，
 * 待授权数据补齐后关掉即可，全程无需改代码。</p>
 *
 * <p>独立上下文 + 独立 H2 库（避免与默认策略的用例共库相互污染）。</p>
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "cim.system.rbac.fallback-to-claims=true",
        "spring.datasource.url=jdbc:h2:mem:cim_system_fallback;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class RbacFallbackToClaimsTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    private LocalAuthorityLoader authorityLoader;

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

    @Test
    void unprovisionedIdentityFallsBackToTokenClaims() {
        TokenClaims claims = new TokenClaims("mig-ext", "mig", Set.of("mds-ap"), Set.of(), "T1", null,
                Set.of("mes:order:list", "mes:order:save"), 1L, Instant.now().plusSeconds(600), Map.of());

        assertThat(authorityLoader.loadAuthorities(claims))
                .containsExactlyInAnyOrder("mes:order:list", "mes:order:save");
    }

    @Test
    void provisionedUserWithoutPermissionAlsoFallsBack() {
        String suffix = "f" + SEQ.incrementAndGet();
        SysPermission perm = new SysPermission();
        perm.applyCode("mes:mig:any");
        perm.setName("mig-" + suffix);
        permissionRepository.save(perm);

        SysRole role = new SysRole();
        role.setCode("mig_empty_" + suffix);
        role.setName("mig_empty_" + suffix);
        roleRepository.save(role);

        SysUser user = new SysUser();
        user.setUsername("mig-user-" + suffix);
        user.setExternalId("mig-user-ext-" + suffix);
        userRepository.save(user);
        userRoleRepository.save(SysUserRole.of(user.getId(), role.getId()));

        TokenClaims claims = new TokenClaims("mig-user-ext-" + suffix, "mig-user-" + suffix,
                Set.of("mds-ap"), Set.of(), "T1", null, Set.of("mes:from:token"), 1L,
                Instant.now().plusSeconds(600), Map.of());

        assertThat(authorityLoader.loadAuthorities(claims))
                .as("已建档但未授予权限 → 回退令牌 claim")
                .containsExactly("mes:from:token");
    }
}
