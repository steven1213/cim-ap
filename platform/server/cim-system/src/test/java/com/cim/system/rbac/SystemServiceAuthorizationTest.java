package com.cim.system.rbac;

import com.cim.core.shared.PageResult;
import com.cim.system.TestApplication;
import com.cim.system.permission.SysPermissionService;
import com.cim.system.support.PermissionCodes;
import com.cim.system.user.SysUser;
import com.cim.system.user.SysUserHist;
import com.cim.system.user.SysUserService;
import com.cim.spring.support.web.PageQuery;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 方法级鉴权 + 全链路落库验证（plan.md M6：「`@PreAuthorize` 命中真实权限」「越权调用被拒」）。
 *
 * <p>同时验证「经服务基类写库 → 自动落历史表」在 cim-system 的实体族上确实成立
 * （历史表由模块自带 DDL 建，见 {@code db/migration/system/h2}）。</p>
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:cim_system_auth;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
class SystemServiceAuthorizationTest {

    @Autowired
    private SysUserService userService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @WithMockUser(authorities = PermissionCodes.USER_SAVE)
    void saveAllowedWithRequiredAuthorityAndHistoryIsWritten() {
        SysUser user = new SysUser();
        user.setUsername("auth-allowed");
        user.setExternalId("auth-allowed-ext");
        user.setDisplayName("允许保存");

        SysUser saved = userService.save(user);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getDeleted()).isFalse();

        List<SysUserHist> history = entityManager
                .createQuery("select h from SysUserHist h where h.bizId = :id", SysUserHist.class)
                .setParameter("id", saved.getId())
                .getResultList();
        assertThat(history)
                .as("主实体写入须自动落 {X}Hist 快照（@History(SNAPSHOT) + 模块自带历史表）")
                .hasSize(1);
        assertThat(history.get(0).getOpType()).isEqualTo("I");
        assertThat(history.get(0).getUsername()).isEqualTo("auth-allowed");
    }

    @Test
    @WithMockUser(authorities = PermissionCodes.ROLE_LIST)
    void loadDeniedWhenAuthorityMissing() {
        assertThatThrownBy(() -> userService.load("any-id"))
                .as("持有 sys:role:list 不等于可读用户 → 方法级鉴权须拒绝")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = PermissionCodes.PERMISSION_LIST)
    void pageAllowedWithMatchingAuthority() {
        PageResult<?> page = permissionService.page(new PageQuery());

        assertThat(page).isNotNull();
        assertThat(page.page()).isEqualTo(1);
    }

    @Test
    @WithMockUser(authorities = PermissionCodes.USER_LIST)
    void pageDeniedOnPermissionResource() {
        assertThatThrownBy(() -> permissionService.page(new PageQuery()))
                .isInstanceOf(AccessDeniedException.class);
    }
}
