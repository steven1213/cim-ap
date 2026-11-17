package com.cim.mds.server.reason;

import java.time.LocalDateTime;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * WIP 原因事件（V16 示例域）——ReasonCode 在 WIP 事务中的<b>不可变历史事实</b>。
 *
 * <p><b>快照，不联表</b>：主数据的当前状态只回答「现在长什么样」；本表以五个快照
 * 字段固定事件发生时刻的码/类型/树路径/归属部门/短名，主数据后续改名、挪部门、
 * 挪树位、停用都不回写这里。按原因码统计 Hold 时长的 Pareto 直接用
 * {@code reason_path_snapshot} 前缀聚合，无需实时递归主数据。</p>
 *
 * <p>示例域定位：WIP 完整建模（批次/工艺/载具关联）随 WIP 域开工时扩展，
 * 本表先落「事件 + 快照」的最小事实结构。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_wip_reason_event", indexes = {
        @Index(name = "idx_cim_wip_reason_event_lot", columnList = "lot_id, occurred_at"),
        @Index(name = "idx_cim_wip_reason_event_reason", columnList = "reason_id")
})
public class CimWipReasonEvent extends BaseDefData {

    /** 批次号（示例域的最小上下文）。 */
    @Column(name = "lot_id", length = 64, nullable = false)
    private String lotId;

    /** 晶圆号（晶圆级事件时使用，可空）。 */
    @Column(name = "wafer_id", length = 64)
    private String waferId;

    /** 工序编码（可空）。 */
    @Column(name = "operation_code", length = 64)
    private String operationCode;

    /** 设备编码（可空）。 */
    @Column(name = "equipment_code", length = 64)
    private String equipmentCode;

    /** 事务类型（HOLD / RELEASE / SCRAP / …，须与原因码 type_key 一致）。 */
    @Column(name = "event_type", length = 32, nullable = false)
    private String eventType;

    /** 原因码引用（保留可追溯主键；展示以快照字段为准）。 */
    @Column(name = "reason_id", length = 64, nullable = false)
    private String reasonId;

    /** 事件发生时刻。 */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** 操作员。 */
    @Column(name = "operator", length = 64)
    private String operator;

    // ---------------------------------------------------------------- 快照五件套

    /** 事件发生时的原因码编码。 */
    @Column(name = "reason_code_snapshot", length = 64, nullable = false)
    private String reasonCodeSnapshot;

    /** 事件发生时的语义域。 */
    @Column(name = "reason_type_snapshot", length = 32, nullable = false)
    private String reasonTypeSnapshot;

    /** 事件发生时的树路径。 */
    @Column(name = "reason_path_snapshot", length = 512, nullable = false)
    private String reasonPathSnapshot;

    /** 事件发生时的归属部门。 */
    @Column(name = "owning_dept_snapshot", length = 64)
    private String owningDeptSnapshot;

    /** 事件发生时的短名。 */
    @Column(name = "reason_name_snapshot", length = 128)
    private String reasonNameSnapshot;
}
