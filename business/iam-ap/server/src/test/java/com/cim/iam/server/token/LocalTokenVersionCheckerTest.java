package com.cim.iam.server.token;

import com.cim.auth.token.TokenClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IAM 自有 {@link LocalTokenVersionChecker}（验证端 in-JVM 版本判定）：
 * 令牌 ver == 当前版本 → 放行；ver 落后（已 bump）→ 拒绝；无 ver / 无 uid → 放行（向后兼容）。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:iam-tvc;DB_CLOSE_DELAY=-1;MODE=MySQL"})
class LocalTokenVersionCheckerTest {

    @Autowired
    TokenVersionService tokenVersionService;

    private LocalTokenVersionChecker checker() {
        return new LocalTokenVersionChecker(tokenVersionService);
    }

    private TokenClaims claims(String uid, Long ver) {
        return new TokenClaims(uid, uid, null, null, null, "jti", null, ver, null, Map.of());
    }

    @Test
    void matchingVersion_isAcceptable() {
        String uid = "tvc-match";
        tokenVersionService.bump(uid); // ver=1
        assertThat(checker().isAcceptable(claims(uid, 1L))).isTrue();
    }

    @Test
    void staleVersion_isRejected() {
        String uid = "tvc-stale";
        tokenVersionService.bump(uid); // ver=1
        tokenVersionService.bump(uid); // ver=2
        // 令牌仍携带旧 ver=1
        assertThat(checker().isAcceptable(claims(uid, 1L))).isFalse();
        // 重新签发携带最新 ver=2 则应被接受
        assertThat(checker().isAcceptable(claims(uid, 2L))).isTrue();
    }

    @Test
    void missingVersion_isAcceptable_backwardCompatible() {
        assertThat(checker().isAcceptable(claims("tvc-nover", null))).isTrue();
    }

    @Test
    void missingUid_isAcceptable() {
        assertThat(checker().isAcceptable(claims(null, 1L))).isTrue();
    }
}
