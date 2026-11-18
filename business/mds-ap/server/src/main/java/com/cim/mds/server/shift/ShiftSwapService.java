package com.cim.mds.server.shift;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.cim.spring.support.web.BizCode;
import com.cim.spring.support.web.BizException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 换班申请服务（V19）——申请 → 对方确认 → 同事务落库。
 *
 * <p>流程（方案定稿）：本期「对方确认即生效」，确认与排班落库同一事务；
 * 班长审批为预留位——{@link #confirm} 里的 {@link #applyToAssignments} 就是
 * 将来接入审批流（approval-ap）后的回调落库缝：届时 confirm 只推进到
 * TARGET_CONFIRMED/APPROVING，审批通过后回调 {@code applyToAssignments} 落库，
 * 申请单结构不变。</p>
 *
 * <p>守门规则：过期班禁换（work_date ≥ 今天）；SWAP 双方班次都必须真实存在且
 * 归属正确；同一班次已有 PENDING 申请禁重；确认落库前复检冲突——排班可能在
 * 申请期间被改（重排/清空），(work_date, user_id) 唯一约束被撞即 2004。</p>
 */
@Service
public class ShiftSwapService {

    /** 申请类型：对等互换。 */
    public static final String TYPE_SWAP = "SWAP";
    /** 申请类型：单向代班。 */
    public static final String TYPE_SUBSTITUTE = "SUBSTITUTE";

    /** 待对方确认。 */
    public static final String ST_PENDING = "PENDING";
    /** 对方已确认（审批预留档，本期不落此态）。 */
    public static final String ST_TARGET_CONFIRMED = "TARGET_CONFIRMED";
    /** 审批中（预留档）。 */
    public static final String ST_APPROVING = "APPROVING";
    /** 已生效（排班已落库）。 */
    public static final String ST_APPLIED = "APPLIED";
    /** 对方拒绝。 */
    public static final String ST_REJECTED = "REJECTED";
    /** 发起人取消。 */
    public static final String ST_CANCELLED = "CANCELLED";

    /** 落到 cim_shift_assignment 的 source 值（区分于 MANUAL/PATTERN/COPY）。 */
    private static final String SOURCE_SWAP = "SWAP";

    private final CimShiftSwapRepository swapRepository;
    private final CimShiftAssignmentRepository assignmentRepository;

    public ShiftSwapService(CimShiftSwapRepository swapRepository,
            CimShiftAssignmentRepository assignmentRepository) {
        this.swapRepository = swapRepository;
        this.assignmentRepository = assignmentRepository;
    }

    // ================================================================ 视图 / 请求

    /** 换班申请行。 */
    public record SwapView(String id, String swapType, String status,
            String initiatorId, String initiatorName, String targetId, String targetName,
            String initiatorWorkDate, String initiatorShiftCode,
            String targetWorkDate, String targetShiftCode,
            String reason, String targetConfirmName, String targetConfirmAt,
            String rejectReason, String appliedAt, Long version) {
    }

    /** 申请请求（myWorkDate/myShiftCode = 发起人自己的班；SWAP 另需对方班定位）。 */
    public record ApplyReq(String swapType, String targetId, String targetName,
            String myWorkDate, String myShiftCode,
            String targetWorkDate, String targetShiftCode, String reason) {
    }

    // ================================================================ 查询

    /** 申请清单（status 过滤可选；create_time 倒序）。 */
    @Transactional(readOnly = true)
    public List<SwapView> list(String status) {
        return swapRepository.findByDeletedFalseOrderByCreateTimeDesc().stream()
                .filter(s -> status == null || status.isBlank() || status.equals(s.getStatus()))
                .map(ShiftSwapService::toView)
                .toList();
    }

    // ================================================================ 申请 / 确认 / 拒绝 / 取消

    /** 发起申请（守门见类注释；落 PENDING）。 */
    @Transactional
    public SwapView apply(ApplyReq req, String operatorId, String operatorName) {
        if (!TYPE_SWAP.equals(req.swapType()) && !TYPE_SUBSTITUTE.equals(req.swapType())) {
            throw new BizException(BizCode.PARAM_INVALID, "申请类型必须是 SWAP 或 SUBSTITUTE");
        }
        if (req.targetId() == null || req.targetId().isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, "对方必填");
        }
        if (req.targetId().equals(operatorId)) {
            throw new BizException(BizCode.PARAM_INVALID, "对方不能是自己");
        }
        LocalDate myDate = LocalDate.parse(req.myWorkDate());
        if (myDate.isBefore(LocalDate.now())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "不能换过去的班");
        }
        CimShiftAssignment mine = requireAssignment(myDate, operatorId, req.myShiftCode(), "发起人");

        boolean swap = TYPE_SWAP.equals(req.swapType());
        LocalDate targetDate = null;
        if (swap) {
            if (req.targetWorkDate() == null || req.targetShiftCode() == null
                    || req.targetShiftCode().isBlank()) {
                throw new BizException(BizCode.PARAM_INVALID, "互换必须提供对方的值班日与班次");
            }
            targetDate = LocalDate.parse(req.targetWorkDate());
            if (targetDate.isBefore(LocalDate.now())) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "不能换过去的班");
            }
            requireAssignment(targetDate, req.targetId(), req.targetShiftCode(), "对方");
            if (targetDate.equals(myDate)) {
                throw new BizException(BizCode.PARAM_INVALID, "互换两班不能同日");
            }
            // 双槽位禁重：任一端已有 PENDING 申请就不允许再叠
            if (swapRepository.existsByInitiatorWorkDateAndInitiatorShiftCodeAndStatusAndDeletedFalse(
                    myDate, req.myShiftCode(), ST_PENDING)
                    || swapRepository.existsByTargetWorkDateAndTargetShiftCodeAndStatusAndDeletedFalse(
                            targetDate, req.targetShiftCode(), ST_PENDING)) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "该班次已有待确认的换班申请");
            }
        } else {
            if (swapRepository.existsByInitiatorWorkDateAndInitiatorShiftCodeAndStatusAndDeletedFalse(
                    myDate, req.myShiftCode(), ST_PENDING)) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED, "该班次已有待确认的换班申请");
            }
        }

        CimShiftSwap e = new CimShiftSwap();
        e.setSwapType(req.swapType());
        e.setStatus(ST_PENDING);
        e.setInitiatorId(operatorId);
        e.setInitiatorName(operatorName);
        e.setTargetId(req.targetId());
        e.setTargetName(req.targetName());
        e.setInitiatorWorkDate(myDate);
        e.setInitiatorShiftCode(req.myShiftCode());
        if (swap) {
            e.setTargetWorkDate(targetDate);
            e.setTargetShiftCode(req.targetShiftCode());
        }
        e.setReason(req.reason());
        return toView(swapRepository.save(e));
    }

    /**
     * 对方确认（本期确认即生效：确认字段与排班落库同一事务；审批预留缝见类注释）。
     */
    @Transactional
    public SwapView confirm(String id, String note, String operatorId, String operatorName) {
        CimShiftSwap e = requireSwap(id);
        if (!ST_PENDING.equals(e.getStatus())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "仅待确认状态的申请可以确认，当前: " + e.getStatus());
        }
        e.setTargetConfirmBy(operatorId);
        e.setTargetConfirmName(operatorName);
        e.setTargetConfirmAt(LocalDateTime.now());
        e.setTargetConfirmNote(note);
        applyToAssignments(e);
        e.setStatus(ST_APPLIED);
        e.setAppliedAt(LocalDateTime.now());
        return toView(swapRepository.save(e));
    }

    /** 对方拒绝（PENDING → REJECTED）。 */
    @Transactional
    public SwapView reject(String id, String reason) {
        CimShiftSwap e = requireSwap(id);
        if (!ST_PENDING.equals(e.getStatus())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "仅待确认状态的申请可以拒绝，当前: " + e.getStatus());
        }
        e.setStatus(ST_REJECTED);
        e.setRejectReason(reason);
        return toView(swapRepository.save(e));
    }

    /** 发起人取消（PENDING → CANCELLED）。 */
    @Transactional
    public SwapView cancel(String id) {
        CimShiftSwap e = requireSwap(id);
        if (!ST_PENDING.equals(e.getStatus())) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    "仅待确认状态的申请可以取消，当前: " + e.getStatus());
        }
        e.setStatus(ST_CANCELLED);
        return toView(swapRepository.save(e));
    }

    // ================================================================ 内部：落库缝（审批接入点）

    /**
     * 把申请落到 cim_shift_assignment——审批流的回调缝：将来接 approval-ap 后，
     * 审批通过的回调里调用本方法即可，申请单与排班模型都不用改。
     */
    private void applyToAssignments(CimShiftSwap e) {
        CimShiftAssignment mine = requireAssignment(e.getInitiatorWorkDate(),
                e.getInitiatorId(), e.getInitiatorShiftCode(), "发起人");

        if (TYPE_SWAP.equals(e.getSwapType())) {
            CimShiftAssignment theirs = requireAssignment(e.getTargetWorkDate(),
                    e.getTargetId(), e.getTargetShiftCode(), "对方");
            // 冲突复检：互换后双方各自接管对方的日期，不能与既有排班撞唯一约束
            if (assignmentRepository.findByWorkDateAndUserIdAndDeletedFalse(
                    e.getTargetWorkDate(), e.getInitiatorId()).isPresent()) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                        "发起人在对方值班日已有排班，无法互换");
            }
            if (assignmentRepository.findByWorkDateAndUserIdAndDeletedFalse(
                    e.getInitiatorWorkDate(), e.getTargetId()).isPresent()) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                        "对方在发起人值班日已有排班，无法互换");
            }
            theirs.setUserId(e.getInitiatorId());
            theirs.setUsername(e.getInitiatorName());
            theirs.setSource(SOURCE_SWAP);
            assignmentRepository.save(theirs);
        } else {
            // 代班：对方在发起人值班日必须空闲，否则撞唯一约束
            if (assignmentRepository.findByWorkDateAndUserIdAndDeletedFalse(
                    e.getInitiatorWorkDate(), e.getTargetId()).isPresent()) {
                throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                        "对方在该值班日已有排班，无法代班");
            }
        }
        mine.setUserId(e.getTargetId());
        mine.setUsername(e.getTargetName());
        mine.setSource(SOURCE_SWAP);
        assignmentRepository.save(mine);
    }

    private CimShiftSwap requireSwap(String id) {
        return swapRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BizException(BizCode.DATA_NOT_FOUND, "换班申请不存在: " + id));
    }

    /** 校验某人某日某班次的真实排班存在且班次一致（不存在/班次对不上都按 2004 拒）。 */
    private CimShiftAssignment requireAssignment(LocalDate workDate, String userId,
            String shiftCode, String role) {
        if (shiftCode == null || shiftCode.isBlank()) {
            throw new BizException(BizCode.PARAM_INVALID, role + "的班次必填");
        }
        CimShiftAssignment a = assignmentRepository
                .findByWorkDateAndUserIdAndDeletedFalse(workDate, userId)
                .orElseThrow(() -> new BizException(BizCode.OPERATION_NOT_ALLOWED,
                        role + "在该日期没有排班: " + workDate));
        if (!a.getShiftCode().equals(shiftCode)) {
            throw new BizException(BizCode.OPERATION_NOT_ALLOWED,
                    role + "该日实际排班为 " + a.getShiftCode() + "，与申请不符");
        }
        return a;
    }

    private static SwapView toView(CimShiftSwap e) {
        return new SwapView(e.getId(), e.getSwapType(), e.getStatus(),
                e.getInitiatorId(), e.getInitiatorName(), e.getTargetId(), e.getTargetName(),
                e.getInitiatorWorkDate().toString(), e.getInitiatorShiftCode(),
                e.getTargetWorkDate() == null ? null : e.getTargetWorkDate().toString(),
                e.getTargetShiftCode(),
                e.getReason(), e.getTargetConfirmName(),
                e.getTargetConfirmAt() == null ? null : e.getTargetConfirmAt().toString(),
                e.getRejectReason(),
                e.getAppliedAt() == null ? null : e.getAppliedAt().toString(),
                e.getVersion());
    }
}
