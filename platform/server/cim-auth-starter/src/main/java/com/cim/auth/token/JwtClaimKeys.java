package com.cim.auth.token;

/**
 * IAM 令牌 claim 键（contract，platform 侧冻结，见 design.md §8 / plan.md T3.8）。
 *
 * <p>这些键由 {@code business/iam-ap} 签发时填充，platform 仅消费，不修改。
 * 任何破坏性变更须经契约评审（plan.md 全局门禁「契约」）。</p>
 */
public final class JwtClaimKeys {

    /** 用户 ID（缺省回退到 sub）。 */
    public static final String USER_ID = "uid";

    /** 用户名（缺省回退到 sub）。 */
    public static final String USERNAME = "uname";

    /** 可进入的 ap 列表（准入依据）。 */
    public static final String APP_CODES = "apps";

    /** ap 内粗角色组（可选）。 */
    public static final String ROLES = "roles";

    /** 租户 ID（多租户隔离 / 数据权限依据）。 */
    public static final String TENANT_ID = "tenantId";

    /** 本 ap 业务权限集（module:res:action，供 @PreAuthorize）。 */
    public static final String AUTHORITIES = "authorities";

    /** 令牌版本（本地吊销 / 权限失效判定）。 */
    public static final String VERSION = "ver";

    private JwtClaimKeys() {
    }
}
