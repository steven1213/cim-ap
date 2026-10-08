package com.cim.iam.server.auth;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.token.RsaKeyService;
import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 独立上下文 + 独立 H2；阈值调低（2 次即锁）以便快速验证（design.md §8.1(i)）
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-lockout;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "cim.iam.auth.lockout.max-attempts=2",
        "cim.iam.auth.lockout.lock-minutes=15",
        "cim.iam.auth.lockout.window-minutes=15"
})
class LoginLockoutTest {

    @Autowired
    private LoginService loginService;
    @Autowired
    private LocalCredentialService credentialService;
    @Autowired
    private AppRegistrationService appService;
    @Autowired
    private IamAuthProperties authProps;
    @Autowired
    private RsaKeyService rsaKeyService;

    private String clientHashFor(String password, String salt) {
        return PasswordDerivation.derive(password, salt, authProps.getPassword().getRounds());
    }

    private void seed(String username, String password) {
        String salt = "salt-" + username;
        if (credentialService.findByUsername(username).isEmpty()) {
            credentialService.registerLocalUser(username, clientHashFor(password, salt), salt);
        }
        if (appService.listApps().stream().noneMatch(a -> "mds-ap".equals(a.getAppCode()))) {
            appService.registerApp("mds-ap", "MDS", 1);
        }
        appService.assignUserToApp(username, "mds-ap", Set.of("ADMIN"));
    }

    @Test
    void wrongPasswordMaxTimesThenCorrectIsRejectedAsLocked() {
        seed("locky", "secret");
        LoginCredentials wrong = new LoginCredentials("locky", "wrong-client-hash", "salt-locky");
        LoginCredentials right = new LoginCredentials("locky",
                clientHashFor("secret", "salt-locky"), "salt-locky");

        // 第 1 次错误：noAdmission，未锁定
        BizException e1 = assertThrows(BizException.class, () -> loginService.login(wrong));
        assertThat(e1.getBizCode()).isEqualTo(BizCode.NO_ADMISSION);

        // 第 2 次错误：触发锁定（仍为 noAdmission，但已写入锁定）
        assertThatThrownBy(() -> loginService.login(wrong)).isInstanceOf(BizException.class);

        // 第 3 次即使口令正确也拒绝：账户已锁定
        BizException locked = assertThrows(BizException.class, () -> loginService.login(right));
        assertThat(locked.getBizCode()).isEqualTo(BizCode.ACCOUNT_LOCKED);
    }

    @Test
    void successResetsCounterAllowingRetry() {
        seed("retry", "secret");
        LoginCredentials wrong = new LoginCredentials("retry", "wrong-client-hash", "salt-retry");
        LoginCredentials right = new LoginCredentials("retry",
                clientHashFor("secret", "salt-retry"), "salt-retry");

        // 1 次错误（未达阈值）
        assertThatThrownBy(() -> loginService.login(wrong)).isInstanceOf(BizException.class);

        // 成功登录 → 清零计数
        LoginService.LoginResult r = loginService.login(right);
        assertThat(r.token()).isNotBlank();

        // 成功后再次错误仍可尝试（计数已重置，仅 1 次，未锁定）
        assertThatThrownBy(() -> loginService.login(wrong)).isInstanceOf(BizException.class);
    }
}
