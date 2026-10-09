package com.cim.iam.server.org;

/** 组织节点状态（与 {@code app.AppStatus} 同构，独立命名以避免跨域语义混淆）。 */
public enum OrgStatus {
    ENABLED,
    DISABLED
}
