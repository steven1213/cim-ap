package com.cim.iam.server;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.auth.LoginCredentials;
import com.cim.iam.server.auth.LoginService;
import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.iam.server.config.IamAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证「首管理员环境变量引导」：库内无 admin 时，{@code BootstrapAdminRunner} 注册 iam-ap 应用、
 * 创建本地凭证（两层派生）、赋 {@code iam-ap:ADMIN}；且引导口令可端到端登录拿到令牌。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:iam-boot;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "cim.iam.auth.bootstrap.enabled=true",
    "cim.iam.auth.bootstrap.admin-username=admin",
    "cim.iam.auth.bootstrap.admin-password=Admin@123456"
})
class BootstrapAdminTest {

    @Autowired
    LocalCredentialService credentialService;
    @Autowired
    AppRegistrationService appRegistrationService;
    @Autowired
    LoginService loginService;
    @Autowired
    IamAuthProperties authProperties;

    @Test
    void bootstrapCreatesAdminWithIamAdminRole() {
        var cred = credentialService.findByUsername("admin");
        assertTrue(cred.isPresent(), "首管理员 admin 应已由引导创建");

        Set<String> apps = appRegistrationService.enabledAppsForUser("admin");
        assertTrue(apps.contains("iam-ap"), "admin 应可进入 iam-ap");

        Map<String, Set<String>> roles = appRegistrationService.rolesByAppForUser("admin");
        assertTrue(roles.containsKey("iam-ap") && roles.get("iam-ap").contains("ADMIN"),
                "admin 应具 iam-ap:ADMIN 角色");

        // 端到端：用引导口令登录应成功返回令牌
        String clientSalt = cred.get().getClientSalt();
        String clientHash = PasswordDerivation.derive("Admin@123456", clientSalt,
                authProperties.getPassword().getRounds());
        var result = loginService.login(new LoginCredentials("admin", clientHash, clientSalt));
        assertNotNull(result.token(), "引导管理员应能成功登录");
        assertNotNull(result.refreshToken(), "登录应返回刷新令牌");
    }
}
