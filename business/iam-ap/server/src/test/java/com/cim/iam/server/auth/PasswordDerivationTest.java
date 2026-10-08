package com.cim.iam.server.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 口令两层派生工具的单测（不依赖 Spring）。
 */
class PasswordDerivationTest {

    @Test
    void deriveIsDeterministicForSameInput() {
        String a = PasswordDerivation.derive("password", "salt", 100_000);
        String b = PasswordDerivation.derive("password", "salt", 100_000);
        assertThat(a).isEqualTo(b).hasSize(64); // 256-bit = 64 hex
    }

    @Test
    void deriveIsSensitiveToPasswordAndSalt() {
        String p1 = PasswordDerivation.derive("password", "salt", 100_000);
        String p2 = PasswordDerivation.derive("password2", "salt", 100_000);
        String s2 = PasswordDerivation.derive("password", "salt2", 100_000);
        assertThat(p1).isNotEqualTo(p2);
        assertThat(p1).isNotEqualTo(s2);
    }

    @Test
    void constantTimeEqualsBehavesCorrectly() {
        String a = PasswordDerivation.derive("password", "salt", 100_000);
        assertThat(PasswordDerivation.constantTimeEquals(a, a)).isTrue();
        assertThat(PasswordDerivation.constantTimeEquals(a, "deadbeef")).isFalse();
        assertThat(PasswordDerivation.constantTimeEquals(a, null)).isFalse();
        assertThat(PasswordDerivation.constantTimeEquals(null, a)).isFalse();
    }
}
