package com.cim.mds.server.shift;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交接班记录（V17）——事务事实 + 责任文书：每班交接时的书面记录。
 *
 * <p>生命周期三态：<b>交班人提交（UNACKED，可改）→ 接班人签收（ACKED）→ 锁定只读</b>。
 * 签收走乐观锁（{@link BaseDefData#getVersion()}）防并发重复签收；签收后任何字段
 * 不可再改——记录是责任边界，事后修改等于篡改。</p>
 *
 * <p>内容三结构化字段（设备异常 / 遗留事项 / 备注）——现场填写规整、后期可统计，
 * 不用富文本。{@code work_date} 归属按班次开始那天（跨天班次的凌晨交接归前一日的
 * {@code work_date}）。人员列存 id + name 快照，语义同 {@link CimShiftAssignment}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_shift_log")
public class CimShiftLog extends BaseDefData {

    /** 值守日（班次开始那天）。 */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** 班次编码（= cim_shift_def.shift_code）。 */
    @Column(name = "shift_code", length = 32, nullable = false)
    private String shiftCode;

    /** 交班人 id（默认当前登录人）。 */
    @Column(name = "handover_user", length = 64, nullable = false)
    private String handoverUser;

    /** 交班人名快照。 */
    @Column(name = "handover_name", length = 64)
    private String handoverName;

    /** 接班人 id（签收人）。 */
    @Column(name = "receiver_user", length = 64)
    private String receiverUser;

    /** 接班人名快照。 */
    @Column(name = "receiver_name", length = 64)
    private String receiverName;

    /** 设备异常（本班期间发生的设备故障/异常/报修事项）。 */
    @Column(name = "equipment_issue", length = 2000)
    private String equipmentIssue;

    /** 遗留事项（本班未结、需接班人继续跟进的事项）。 */
    @Column(name = "pending_items", length = 2000)
    private String pendingItems;

    /** 备注（其他需要告知的事项）。 */
    @Column(name = "remarks", length = 2000)
    private String remarks;

    /** 是否已签收（true = 锁定只读）。 */
    @Column(name = "acked", nullable = false)
    private Boolean acked = Boolean.FALSE;

    /** 签收人 id。 */
    @Column(name = "ack_by", length = 64)
    private String ackBy;

    /** 签收人名快照。 */
    @Column(name = "ack_name", length = 64)
    private String ackName;

    /** 签收时间。 */
    @Column(name = "ack_at")
    private LocalDateTime ackAt;
}
