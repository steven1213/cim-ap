package com.cim.mds.server.equipment;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备子单元（EquipmentUnit）——设备结构树节点（SEMI E120 CEM 口径）。
 *
 * <p>判据（E164）：「主动改变/测量晶圆 且 其加工行为受 recipe 控制」= {@code MODULE}
 * （工艺腔）；否则一律 {@code SUBSYSTEM}（LoadPort、Aligner、Robot、LoadLock 等，
 * 它们不做工艺、建成本地导致 E157 状态机挂错位置、腔体级 OEE 失真——行业高频错误）。</p>
 *
 * <p>v2（2026-11-24）增强：LOAD_PORT 端口行为属性（E87 口径）——{@code carrier_category}
 * （CASSETTE/FOUP/FOSB/MAGAZINE/OTHER，6/8/12 寸载具差异用属性表达）与
 * {@code load_purpose}（BOTH/INPUT/OUTPUT）；MODULE 可挂 {@code contamination_level}
 * （污染等级，前道交叉污染控制，行业 1-5 级）；{@code capacity} 通用化——不再
 * LOAD_PORT 专用，BUFFER（机台内缓存位）同样维护容量。</p>
 *
 * <p>不设编辑锁三列：子单元在设备编辑弹窗内维护，写操作守门父设备编辑锁
 * （与能力关联同口径）；随设备删除物理级联（软删会撞唯一索引）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_equipment_unit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_equipment_unit", columnNames = {"equipment_id", "unit_code", "deleted"})
})
public class CimEquipmentUnit extends BaseDefData {

    /** 所属设备主键（cim_equipment.id）。 */
    @Column(name = "equipment_id", length = 64, nullable = false)
    private String equipmentId;

    /** 子单元编码（设备内唯一，例：LP1 / PM1）。 */
    @Column(name = "unit_code", length = 64, nullable = false)
    private String unitCode;

    /** 子单元名称（例：光刻机#1 载具端口 1）。 */
    @Column(name = "unit_name", length = 128, nullable = false)
    private String unitName;

    /** E120 节点类：MODULE（受 recipe 控制的工艺腔）/ SUBSYSTEM（不做工艺的复杂部件）。 */
    @Column(name = "unit_class", length = 16, nullable = false)
    private String unitClass;

    /** 具体形态：LOAD_PORT / PROCESS_CHAMBER / ALIGNER / ROBOT / LOAD_LOCK / BUFFER / OTHER。 */
    @Column(name = "unit_type", length = 32, nullable = false)
    private String unitType;

    /** 当前状态快照（mds.unit_state 字典；E87 端口态 / E157 腔态 / E116 EPT 合并词表）。 */
    @Column(name = "state_code", length = 32)
    private String stateCode;

    /** 端口号（LOAD_PORT 专用，例：1 = LP1）。 */
    @Column(name = "port_no")
    private Integer portNo;

    /** 容量（LOAD_PORT=载具容量；BUFFER=缓存位数——v2 起两形态共用）。 */
    @Column(name = "capacity")
    private Integer capacity;

    /** 载具类别（LOAD_PORT 专用：CASSETTE/FOUP/FOSB/MAGAZINE/OTHER）。 */
    @Column(name = "carrier_category", length = 16)
    private String carrierCategory;

    /** 装载目的（LOAD_PORT 专用：BOTH/INPUT/OUTPUT）。 */
    @Column(name = "load_purpose", length = 16)
    private String loadPurpose;

    /** 污染等级（MODULE 专用，行业 1-5 级，可空）。 */
    @Column(name = "contamination_level", length = 16)
    private String contaminationLevel;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
