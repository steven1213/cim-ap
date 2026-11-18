package com.cim.mds.server.shift;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 换班申请（V19）——员工自助换班/代班的申请单事实：发起 → 对方确认 → 自动落库。
 *
 * <p>两种类型：{@code SWAP} 对等互换（发起人的班 ↔ 对方的班，两行排班同事务交换
 * 归属）；{@code SUBSTITUTE} 单向代班（对方顶发起人的班，对方班次不动）。</p>
 *
 * <p>状态机（方案定稿）：{@code PENDING → APPLIED / REJECTED / CANCELLED}——本期
 * 对方确认即生效（确认与落库同一事务）；{@code APPROVING} 为班长审批预留档位、
 * {@code approval_id} 为审批单号预留列，将来接 approval-ap 时按
 * {@code PENDING → TARGET_CONFIRMED → APPROVING → APPLIED} 平滑升级，表结构不变。</p>
 *
 * <p>治理纪律：work_date 记班次开始那天；双方姓名快照冗余；过期班禁换、
 * 同班次未完结申请禁重（服务端守门）；落库前复检冲突（申请期间排班可能已改）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_shift_swap")
public class CimShiftSwap extends BaseDefData {

    /** 申请类型：SWAP 对等互换 / SUBSTITUTE 单向代班。 */
    @Column(name = "swap_type", length = 16, nullable = false)
    private String swapType;

    /** 状态：PENDING / TARGET_CONFIRMED / APPROVING / APPLIED / REJECTED / CANCELLED。 */
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    /** 发起人（把班换出去的一方；username 快照防人员变动丢可读性）。 */
    @Column(name = "initiator_id", length = 64, nullable = false)
    private String initiatorId;

    @Column(name = "initiator_name", length = 64)
    private String initiatorName;

    /** 对方（SWAP 接收发起人班次并让出自己的班；SUBSTITUTE 仅顶班）。 */
    @Column(name = "target_id", length = 64, nullable = false)
    private String targetId;

    @Column(name = "target_name", length = 64)
    private String targetName;

    /** 发起人的值班日（班次开始那天）。 */
    @Column(name = "initiator_work_date", nullable = false)
    private LocalDate initiatorWorkDate;

    /** 发起人的班次。 */
    @Column(name = "initiator_shift_code", length = 32, nullable = false)
    private String initiatorShiftCode;

    /** 对方的值班日（SWAP 必填；SUBSTITUTE 空）。 */
    @Column(name = "target_work_date")
    private LocalDate targetWorkDate;

    /** 对方的班次（SWAP 必填；SUBSTITUTE 空）。 */
    @Column(name = "target_shift_code", length = 32)
    private String targetShiftCode;

    /** 换班原因（留给对方与班长看）。 */
    @Column(name = "reason", length = 512)
    private String reason;

    /** 对方确认摘要（确认即生效，本期确认人 = 落库执行人）。 */
    @Column(name = "target_confirm_by", length = 64)
    private String targetConfirmBy;

    @Column(name = "target_confirm_name", length = 64)
    private String targetConfirmName;

    @Column(name = "target_confirm_at")
    private LocalDateTime targetConfirmAt;

    /** 对方确认备注。 */
    @Column(name = "target_confirm_note", length = 512)
    private String targetConfirmNote;

    /** 拒绝原因（对方拒单时填）。 */
    @Column(name = "reject_reason", length = 512)
    private String rejectReason;

    /** 生效时间（排班落库完成时刻）。 */
    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    /** 审批单号（预留：接 approval-ap 后回填，本期恒空）。 */
    @Column(name = "approval_id", length = 64)
    private String approvalId;
}
