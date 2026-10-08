package com.cim.iam.server.auth;

import com.cim.iam.server.token.TokenIssuerService;
import com.cim.iam.server.token.TokenIssueRequest;
import com.cim.spring.support.web.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 刷新令牌服务：签发 → 轮转（旧令牌作废）→ 撤销（再次使用失败）。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:iam-refresh;DB_CLOSE_DELAY=-1;MODE=MySQL")
class RefreshTokenServiceTest {

    @Autowired
    RefreshTokenService refreshTokenService;
    @Autowired
    TokenIssuerService tokenIssuerService;

    @Test
    void issueThenRotateThenRevoke() {
        // 签发一个真实访问令牌，用于关联刷新令牌 jti
        String access = tokenIssuerService.issue(new TokenIssueRequest("u1", "u1", null));
        String rt1 = refreshTokenService.issue("u1", access);

        RefreshTokenService.RefreshedTokens rotated = refreshTokenService.rotate(rt1);
        assertThat(rotated.accessToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotEqualTo(rt1); // 轮转后旧刷新令牌作废

        // 旧刷新令牌已被作废 → 再次使用应失败
        assertThatThrownBy(() -> refreshTokenService.rotate(rt1))
                .isInstanceOf(BizException.class);

        // 主动撤销新刷新令牌 → 失效
        refreshTokenService.revoke(rotated.refreshToken());
        assertThatThrownBy(() -> refreshTokenService.rotate(rotated.refreshToken()))
                .isInstanceOf(BizException.class);
    }
}
