package com.cim.iam.server.auth;

import com.cim.iam.server.app.AppRegistrationService;
import com.cim.iam.server.config.IamAuthProperties;
import com.cim.iam.server.token.RsaKeyService;
import com.cim.spring.support.web.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 独立上下文 + 独立 H2（避免与其它 IAM 测试共享库导致数据串扰）
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:iam-login-unit;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
class LoginServiceTest {

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

    private void seedAlice() {
        String salt = "salt-alice";
        credentialService.registerLocalUser("alice", clientHashFor("secret", salt), salt);
        appService.registerApp("mds-ap", "MDS", 1);
        appService.assignUserToApp("alice", "mds-ap", Set.of("ADMIN"));
    }

    @Test
    void loginSuccessIssuesTokenWithAppsAndBumpsVersion() {
        seedAlice();
        LoginService.LoginResult r1 = loginService.login(
                new LoginCredentials("alice", clientHashFor("secret", "salt-alice"), "salt-alice"));

        assertThat(r1.token()).isNotBlank();
        assertThat(r1.tokenType()).isEqualTo("Bearer");
        assertThat(r1.expiresInSeconds()).isGreaterThan(0);

        long ver1 = parseVer(r1.token());
        assertThat(parseUid(r1.token())).isEqualTo("alice");
        assertThat(parseApps(r1.token())).contains("mds-ap");
        assertThat(ver1).isPositive();

        // 再次登录：版本应自增（旧令牌随之失效）
        LoginService.LoginResult r2 = loginService.login(
                new LoginCredentials("alice", clientHashFor("secret", "salt-alice"), "salt-alice"));
        long ver2 = parseVer(r2.token());
        assertThat(ver2).isGreaterThan(ver1);
    }

    @Test
    void loginWrongCredentialRejected() {
        String salt = "salt-bob";
        credentialService.registerLocalUser("bob", clientHashFor("right", salt), salt);

        assertThatThrownBy(() -> loginService.login(
                new LoginCredentials("bob", "wrong-client-hash", salt)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void loginUnknownUserRejected() {
        assertThatThrownBy(() -> loginService.login(
                new LoginCredentials("ghost", "x", "s")))
                .isInstanceOf(BizException.class);
    }

    @SuppressWarnings("unchecked")
    private long parseVer(String token) {
        RSAPublicKey pub = (RSAPublicKey) rsaKeyService.getPublicKey();
        Claims c = Jwts.parser().verifyWith(pub).build().parseSignedClaims(token).getPayload();
        return ((Number) c.get("ver")).longValue();
    }

    private String parseUid(String token) {
        RSAPublicKey pub = (RSAPublicKey) rsaKeyService.getPublicKey();
        Jws<Claims> jws = Jwts.parser().verifyWith(pub).build().parseSignedClaims(token);
        return jws.getPayload().get("uid", String.class);
    }

    private Set<String> parseApps(String token) {
        RSAPublicKey pub = (RSAPublicKey) rsaKeyService.getPublicKey();
        Claims c = Jwts.parser().verifyWith(pub).build().parseSignedClaims(token).getPayload();
        return new LinkedHashSet<>((List<String>) c.get("apps"));
    }
}
