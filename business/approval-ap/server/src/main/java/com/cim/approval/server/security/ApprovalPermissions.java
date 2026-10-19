package com.cim.approval.server.security;

/**
 * 审批平台平台权限码常量（G1 / D6）。
 *
 * <p>两层授权模型：①本类平台码接 cim-system {@code sys_permission} 种子；②定义级「可发起人/可管理者」
 * 由 {@link SecurityGuard} 的三级判定承载（平台超管跨域 / 域管理员本 appCode / 定义管理者本定义）。</p>
 *
 * <p>约定：平台超管权限在 IAM 侧以 {@code is_super} 表达，经认证 starter 的 {@code LocalAuthorityLoader}
 * 展开为 {@code ROLE_SUPER_ADMIN} 权威；本平台据此放行跨域管理与定义级操作。</p>
 */
public final class ApprovalPermissions {

    /** 流程定义管理：部署 / 停用启用 / 复制。 */
    public static final String DEFINITION_MANAGE = "approval:definition:manage";

    /** 回调配置管理：注册 / 变更 / 停用。 */
    public static final String CALLBACK_MANAGE = "approval:callback:manage";

    /** 审计查看：审计哈希链查询。 */
    public static final String AUDIT_VIEW = "approval:audit:view";

    /** 实例管理：强制终止 / 管理员干预等。 */
    public static final String INSTANCE_ADMIN = "approval:instance:admin";

    /** 平台超管权威（IAM is_super 展开）。 */
    public static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    private ApprovalPermissions() {
    }
}
