package com.cim.iam.server.profile;

/** 用户档案状态（identity-directory.md §3.2）：AD 侧 {@code userAccountControl} 映射为 {@code INACTIVE}。 */
public enum UserStatus {
    ACTIVE,
    INACTIVE
}
