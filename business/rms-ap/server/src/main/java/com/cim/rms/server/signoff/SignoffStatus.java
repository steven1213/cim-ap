package com.cim.rms.server.signoff;

/** 签核单状态（Req.md FR-L4、§3.2；签核通过 → 版本 ACTIVE，拒绝 → 版本退回 DRAFT）。 */
public enum SignoffStatus {
    /** 待审批（版本处于 PENDING_SIGN）。 */
    PENDING,
    /** 已通过（版本已转 ACTIVE）。 */
    APPROVED,
    /** 已拒绝（版本已退回 DRAFT）。 */
    REJECTED
}
