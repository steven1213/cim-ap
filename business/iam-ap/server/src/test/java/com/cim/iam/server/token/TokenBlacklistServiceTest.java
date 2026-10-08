package com.cim.iam.server.token;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 令牌黑名单存储与查询（幂等吊销）。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:iam-bl;DB_CLOSE_DELAY=-1;MODE=MySQL")
class TokenBlacklistServiceTest {

    @Autowired
    TokenBlacklistService blacklistService;

    @Test
    void revokeThenIsRevokedAndIdempotent() {
        assertThat(blacklistService.isRevoked("jti-x")).isFalse();

        blacklistService.revoke("jti-x", "u1", Instant.now().plusSeconds(60));
        assertThat(blacklistService.isRevoked("jti-x")).isTrue();

        // 幂等：重复吊销不报错
        blacklistService.revoke("jti-x", "u1", Instant.now().plusSeconds(60));
        assertThat(blacklistService.isRevoked("jti-x")).isTrue();

        // 未拉黑的 jti 仍为 false
        assertThat(blacklistService.isRevoked("jti-y")).isFalse();
    }
}
