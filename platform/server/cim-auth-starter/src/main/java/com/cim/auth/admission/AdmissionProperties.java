package com.cim.auth.admission;

/**
 * 准入配置（对应 design.md §8 的「准入判定」）。
 *
 * <p>IAM 的「刀」止于准入：令牌 {@code apps} claim 必须包含本 ap 的接入码，
 * 否则 platform 侧直接 403。业务内部的菜单/按钮/数据权限由各 ap 自管。</p>
 */
public class AdmissionProperties {

    /** 本 ap 接入码（如 {@code mds-ap}/{@code mes-ap}）。来自 IAM 下发的 {@code apps} claim。 */
    private String appCode;

    /** 是否强制准入检查；关闭则所有已签名的令牌都放行（仅用于本地调试）。 */
    private boolean enabled = true;

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
