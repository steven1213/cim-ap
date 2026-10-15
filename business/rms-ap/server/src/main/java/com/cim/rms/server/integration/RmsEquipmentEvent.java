package com.cim.rms.server.integration;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * GEM 设备事件（SEMI E30 Collection Event 在 RMS 侧的落库载体）。
 *
 * <p>与「轮询巡检」（FR-C6）互补：巡检是主机定期问，事件是设备主动报。行业里两者都要有——
 * 事件负责<b>及时性</b>（工程师在机台改完配方，主机几秒内就知道），巡检负责<b>兜底</b>
 * （事件链路断了的设备仍能在巡检中被发现）。</p>
 *
 * <p>{@code idemKey} 唯一：EAP 断线重连后重放同一事件不会重复处置（Req §5.2 消费端去重语义）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "rms_equipment_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_ee_idem_key", columnNames = {"idem_key"}))
public class RmsEquipmentEvent extends BaseDefData {

    /** 幂等键（EAP 侧 eventId，或由「设备+PPID+类型+变更类型+发生时间」合成）。 */
    @Column(name = "idem_key", length = 200, nullable = false)
    private String idemKey;

    @Column(name = "tool_id", length = 64, nullable = false)
    private String toolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 32, nullable = false)
    private EquipmentEventType eventType;

    /** 机台侧配方名（GEM PPID）。 */
    @Column(name = "ppid", length = 128)
    private String ppid;

    /** 变更类型（GEM PPChangeStatus，仅 {@code PP_CHANGE} 有意义）。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 16)
    private EquipmentChangeType changeType;

    /** 发起人（机台操作员/系统，GEM 事件可选携带）。 */
    @Column(name = "op_id", length = 128)
    private String opId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    /** 原始事件载荷（JSON，留档以便复盘；{@code PPError} 等明细在此）。 */
    @Lob
    @Column(name = "payload")
    private String payload;

    /** 设备侧事件发生时间（业务时间，区别于落库审计时间）。 */
    @Column(name = "occurred_at")
    private Instant occurredAt;

    /** 是否已处置（处置逻辑跑完即置真，避免重放重复处置）。 */
    @Column(name = "handled", nullable = false)
    private boolean handled = false;

    /** 处置结论（人可读，供运维与审计追溯）。 */
    @Column(name = "handling_result", length = 256)
    private String handlingResult;

    /** 是否判定为漂移。 */
    @Column(name = "drift_detected", nullable = false)
    private boolean driftDetected = false;

    /** 是否已报警。 */
    @Column(name = "alarm_raised", nullable = false)
    private boolean alarmRaised = false;

    /** 是否已挂 Hold。 */
    @Column(name = "hold_applied", nullable = false)
    private boolean holdApplied = false;
}
