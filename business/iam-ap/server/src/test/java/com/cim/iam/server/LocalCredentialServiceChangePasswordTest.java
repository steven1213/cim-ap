package com.cim.iam.server;

import com.cim.iam.server.auth.LocalCredentialService;
import com.cim.iam.server.auth.PasswordDerivation;
import com.cim.spring.support.web.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@code changePassword} 已切换为接收客户端第一层派生的 {@code clientHash}（与登录一致，
 * 明文口令不出浏览器）：服务端仅做第二层派生校验 + 入库新 clientHash/newClientSalt。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:iam-cpw;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "cim.iam.auth.bootstrap.enabled=false",
    "cim.iam.auth.password.pepper=test-pepper-changepw",
    "cim.iam.auth.password.rounds=100000"
})
class LocalCredentialServiceChangePasswordTest {

    @Autowired
    LocalCredentialService credentialService;

    private static final int ROUNDS = 100_000;
    private static final String OLD_RAW = "Old@123456";
    private static final String NEW_RAW = "New@654321";
    private static final String OLD_CLIENT_SALT = "old-clientsalt-1234567890abcdef";
    private static final String NEW_CLIENT_SALT = "new-clientsalt-abcdef1234567890";

    @Test
    void changePassword_acceptsClientHash_andSwapsToNewPassword() {
        String username = "cpw-user";
        // 注册：提交旧口令第一层派生的 clientHash（模拟登录契约）
        String oldClientHash = PasswordDerivation.derive(OLD_RAW, OLD_CLIENT_SALT, ROUNDS);
        credentialService.registerLocalUser(username, oldClientHash, OLD_CLIENT_SALT);

        // 改密：携带旧/新 clientHash（均由客户端第一层派生）+ 新随机盐
        String newClientHash = PasswordDerivation.derive(NEW_RAW, NEW_CLIENT_SALT, ROUNDS);
        assertDoesNotThrow(() ->
                credentialService.changePassword(username, oldClientHash, newClientHash, NEW_CLIENT_SALT));

        // 新口令可验过，旧口令失效
        assertTrue(credentialService.verifyPassword(username, NEW_RAW), "改密后新口令应可校验");
        assertFalse(credentialService.verifyPassword(username, OLD_RAW), "改密后旧口令应失效");
    }

    @Test
    void changePassword_wrongOldCredential_rejected() {
        String username = "cpw-wrong";
        String oldClientHash = PasswordDerivation.derive(OLD_RAW, OLD_CLIENT_SALT, ROUNDS);
        credentialService.registerLocalUser(username, oldClientHash, OLD_CLIENT_SALT);

        String newClientHash = PasswordDerivation.derive(NEW_RAW, NEW_CLIENT_SALT, ROUNDS);
        String bogusOld = PasswordDerivation.derive("WrongPassword!", OLD_CLIENT_SALT, ROUNDS);
        assertThrows(BizException.class,
                () -> credentialService.changePassword(username, bogusOld, newClientHash, NEW_CLIENT_SALT),
                "旧口令校验失败应抛 BizException");
    }
}
