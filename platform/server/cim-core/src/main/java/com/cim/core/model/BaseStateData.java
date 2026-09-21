package com.cim.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 状态表 / 当前态基类（高频变更：设备实时状态、工单、批次）。
 *
 * <p>声明 {@code @History(STATE_LOG)} 时历史只记状态变迁流水，落该实体**专属**
 * 状态流水表 {@code {X}StateLog}，避免历史表被秒级写入撑爆（见 README §9）。</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseStateData extends Auditable {

    /** 主键（应用层时间有序 ID，见 README §3）。 */
    @Id
    @Column(name = "id", length = 64, updatable = false, nullable = false)
    private String id;

    /** 当前状态（如 IDLE / RUN / DOWN / MAINT）。 */
    @Column(name = "current_state", length = 32)
    private String currentState;

    /** 进入当前状态的时间。 */
    @Column(name = "state_since")
    private LocalDateTime stateSince;

    /** 逻辑删除标记。 */
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = Boolean.FALSE;
}
