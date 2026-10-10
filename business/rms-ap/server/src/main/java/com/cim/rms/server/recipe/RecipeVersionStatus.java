package com.cim.rms.server.recipe;

/** 配方版本状态（Req.md §3.2 状态机；签核流 W2 接入 PENDING_SIGN）。 */
public enum RecipeVersionStatus {
    /** 新建/编辑中（可改 Body/Spec，可删）。 */
    DRAFT,
    /** 签核中（W2：审批流接入后启用；预设限时使用 TEMP_ALLOWED 亦在 W2+）。 */
    PENDING_SIGN,
    /** 生效（同 Recipe 至多一个，Req 14）。 */
    ACTIVE,
    /** 失效（被新版本顶替或手动禁用；仅可查/可复制为 DRAFT）。 */
    OBSOLETE
}
