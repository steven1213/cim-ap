package com.cim.iam.server.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 各测试类独立上下文 + 独立 H2 命名库（H2 命名内存库为 JVM 级共享，须用不同库名隔离，避免数据串扰）
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:iam-app;DB_CLOSE_DELAY=-1;MODE=MySQL")
class AppRegistrationServiceTest {

    @Autowired
    AppRegistrationService service;

    @Test
    void registerAssignQueryAndRevoke() {
        service.registerApp("mds-ap", "MDS", 1);
        service.assignUserToApp("u1", "mds-ap", Set.of("ADMIN"));

        assertThat(service.enabledAppsForUser("u1")).contains("mds-ap");
        assertThat(service.rolesByAppForUser("u1")).containsEntry("mds-ap", Set.of("ADMIN"));

        // 禁用 app 后，即便分配仍在，也不计入可进入集合
        service.disableApp("mds-ap");
        assertThat(service.enabledAppsForUser("u1")).doesNotContain("mds-ap");

        // 重新启用并撤销分配
        service.updateApp("mds-ap", "MDS", AppStatus.ENABLED);
        service.assignUserToApp("u1", "mds-ap", Set.of());
        service.revokeUserFromApp("u1", "mds-ap");
        assertThat(service.enabledAppsForUser("u1")).doesNotContain("mds-ap");
    }

    @Test
    void duplicateAppCodeRejected() {
        service.registerApp("mes-ap", "MES", 2);
        assertThatThrownBy(() -> service.registerApp("mes-ap", "MES-dup", 3))
                .isInstanceOf(com.cim.spring.support.web.BizException.class);
    }

    @Test
    void assignUnknownAppRejected() {
        assertThatThrownBy(() -> service.assignUserToApp("u9", "no-such-app", Set.of()))
                .isInstanceOf(com.cim.spring.support.web.BizException.class);
    }
}
