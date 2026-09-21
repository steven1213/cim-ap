package com.cim.auth.admission;

import com.cim.auth.token.TokenClaims;

/**
 * 准入判定：令牌 {@code apps} claim 是否包含本 ap 接入码（design.md §8 / T3.4）。
 *
 * <p>IAM 的「刀」止于此处：不含本 ap 码 → 直接 403，业务内部权限与此无关。
 * 未配置 {@code appCode} 或关闭准入开关时放行（本地调试用）。</p>
 */
public class AppAdmissionChecker {

    private final String appCode;
    private final boolean enabled;

    public AppAdmissionChecker(AdmissionProperties props) {
        this.appCode = props.getAppCode();
        this.enabled = props.isEnabled();
    }

    public boolean check(TokenClaims claims) {
        if (!enabled) {
            return true;
        }
        if (appCode == null || appCode.isBlank()) {
            return true;
        }
        return claims.apps() != null && claims.apps().contains(appCode);
    }
}
