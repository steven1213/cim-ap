package com.cim.mds.server.equipment;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备↔能力关联（cim_equipment_capability）。
 *
 * <p>纯关联表：无编辑锁（锁是「主数据编辑互斥」，关联随设备编辑产生）。
 * capability_code 逻辑引用 {@link CimCapability}，删除守门在 CapabilityAdminService。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_equipment_capability", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_eqp_cap", columnNames = {"equipment_id", "capability_code", "deleted"})
})
public class CimEquipmentCapability extends BaseDefData {

    /** 设备主键（cim_equipment.id）。 */
    @Column(name = "equipment_id", length = 64, nullable = false)
    private String equipmentId;

    /** 能力编码（cim_capability.capability_code）。 */
    @Column(name = "capability_code", length = 64, nullable = false)
    private String capabilityCode;

    /** 资格适用晶圆尺寸（英寸数字串，mds.wafer_size 字典；空 = 全尺寸通用）。 */
    @Column(name = "wafer_size", length = 16)
    private String waferSize;
}
