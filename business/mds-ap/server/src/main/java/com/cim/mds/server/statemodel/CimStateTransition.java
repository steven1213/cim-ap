package com.cim.mds.server.statemodel;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 状态转换规则（StateTransition）——「允许从哪个状态转到哪个状态」的 allowlist 表。
 *
 * <p>行业口径（SEMI E30 GEM State Transition Reporting）：转换 = 契约（旧态→新态 +
 * 触发条件），Host 按表校验、非法迁移拒绝上报。trigger_note 即契约里的触发条件说明
 * （如 SBY→PRD=「派工开始」）。</p>
 *
 * <p>轻量表不带编辑锁三列（与 cim_equipment_capability 同口径）。矩阵勾选开关 =
 * 建/物理删行（软删会撞唯一索引，同 cim_equipment_capability 的物理删口径）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_state_transition", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_state_transition",
                columnNames = {"model_code", "from_state", "to_state", "deleted"})
})
public class CimStateTransition extends BaseDefData {

    /** 所属状态模型编码（cim_state_model.model_code）。 */
    @Column(name = "model_code", length = 64, nullable = false)
    private String modelCode;

    /** 旧态（from）。 */
    @Column(name = "from_state", length = 64, nullable = false)
    private String fromState;

    /** 新态（to）。 */
    @Column(name = "to_state", length = 64, nullable = false)
    private String toState;

    /** 触发条件说明（E30「转换=契约」，如「派工开始」；可空）。 */
    @Column(name = "trigger_note", length = 255)
    private String triggerNote;
}
