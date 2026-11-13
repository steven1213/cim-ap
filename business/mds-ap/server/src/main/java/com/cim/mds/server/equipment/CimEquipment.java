package com.cim.mds.server.equipment;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备（Equipment）——设备域的实例主数据（一张表，按类型 category 分页视图）。
 *
 * <p>位置引用用 {@code location_type + location_id}（挂主键）：Area/Bay/Stage 编码是
 * 组合唯一（fab+area 等），全局不唯一，单 code 引用有歧义。校验规则见
 * {@link EquipmentAdminService}：PROCESS 必须绑 STAGE，其余类别绑 FAB/AREA/BAY。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_equipment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_equipment_code", columnNames = {"equipment_code", "deleted"})
})
public class CimEquipment extends EditLockDefData {

    /** 设备编码（全局唯一，例：EQP-OHT-001）。 */
    @Column(name = "equipment_code", length = 64, nullable = false)
    private String equipmentCode;

    /** 设备名称（例：OHT 001 号车）。 */
    @Column(name = "equipment_name", length = 128, nullable = false)
    private String equipmentName;

    /** 类型编码（逻辑引用 cim_equipment_type.type_code，页面归属的依据）。 */
    @Column(name = "type_code", length = 64, nullable = false)
    private String typeCode;

    /** 位置层类型：FAB / AREA / BAY / STAGE。 */
    @Column(name = "location_type", length = 16, nullable = false)
    private String locationType;

    /** 位置记录主键（cim_fab/cim_area/cim_bay/cim_stage.id，按 location_type 定表）。 */
    @Column(name = "location_id", length = 64, nullable = false)
    private String locationId;

    /** 当前状态快照（mds.equip_state 字典，E10 六态口径；流转归 EAP 运行时，此处只存快照）。 */
    @Column(name = "state_code", length = 32)
    private String stateCode;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
