package com.cim.iam.server.audit;

/**
 * 审计事件类型（受控词表）。
 *
 * <p>前端按 {@code name()} 映射中文标签；新增类型只需在此登记，无需改表。</p>
 */
public enum AuditType {
    /** 登录成功。 */
    LOGIN_SUCCESS,
    /** 登录失败（口令错误 / 用户不存在）。 */
    LOGIN_FAILURE,
    /** 登录被拒（账户锁定或已禁用）。 */
    LOGIN_REJECTED,
    /** 登出。 */
    LOGOUT,
    /** 自助改密。 */
    PASSWORD_CHANGED,
    /** 管理员重置口令。 */
    PASSWORD_RESET,
    /** 创建用户账号。 */
    USER_CREATED,
    /** 启用账号。 */
    USER_ENABLED,
    /** 禁用账号。 */
    USER_DISABLED,
    /** 删除账号。 */
    USER_DELETED,
    /** 解除登录锁定。 */
    USER_UNLOCKED,
    /** 注册应用。 */
    APP_REGISTERED,
    /** 更新应用（名称/状态）。 */
    APP_UPDATED,
    /** 授予准入。 */
    ADMISSION_GRANTED,
    /** 撤销准入。 */
    ADMISSION_REVOKED,
    /** 强制下线（bump 令牌版本）。 */
    SESSION_REVOKED
}
