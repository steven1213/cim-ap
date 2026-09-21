package com.cim.auth;

import com.cim.auth.admission.AdmissionProperties;
import com.cim.auth.admission.AppAdmissionChecker;
import com.cim.auth.token.TokenClaims;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T3.4：准入判定（apps claim 含本 ap 码才放行）。 */
class AppAdmissionCheckerTest {

    private TokenClaims claims(Set<String> apps) {
        return new TokenClaims("u1", "u1", apps, Set.of(), "t", Set.of(), 1L, null, Map.of());
    }

    @Test
    void containsAppCode_true() {
        AdmissionProperties p = new AdmissionProperties();
        p.setAppCode("mds-ap");
        AppAdmissionChecker checker = new AppAdmissionChecker(p);
        assertTrue(checker.check(claims(Set.of("mds-ap", "iam-ap"))));
    }

    @Test
    void missingAppCode_false() {
        AdmissionProperties p = new AdmissionProperties();
        p.setAppCode("mds-ap");
        AppAdmissionChecker checker = new AppAdmissionChecker(p);
        assertFalse(checker.check(claims(Set.of("mes-ap"))));
    }

    @Test
    void disabled_true() {
        AdmissionProperties p = new AdmissionProperties();
        p.setAppCode("mds-ap");
        p.setEnabled(false);
        AppAdmissionChecker checker = new AppAdmissionChecker(p);
        assertTrue(checker.check(claims(Set.of("mes-ap"))));
    }
}
