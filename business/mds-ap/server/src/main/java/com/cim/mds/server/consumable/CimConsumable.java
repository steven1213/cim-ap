package com.cim.mds.server.consumable;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 耗材（Consumable）——生产辅助物料台账级主数据（V34）。
 *
 * <p>行业口径：编码/规格型号/类别/计量单位/寿命管理/库存水位。领用出库与盘点
 * 流水归 MES/物料域，主数据只存当前水位（stock_qty）与安全水位（min_qty）。
 * 寿命管理三轨（mds.consumable_life_type 字典）：CYCLE 按使用次数 / DAYS 按天数 /
 * QTY 按产量；life_limit = 寿命上限、life_used = 已消耗、warn_percent = 预警阈值（%），
 * 到期判定与预警触发归运行域。</p>
 *
 * <p>cons_code 全局唯一、建后不可改；编辑锁三列与全库主数据同款。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_consumable", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_consumable", columnNames = {"cons_code", "deleted"})
})
public class CimConsumable extends EditLockDefData {

    /** 耗材编码（全局唯一，例：CM-SL-001）。 */
    @Column(name = "cons_code", length = 64, nullable = false)
    private String consCode;

    /** 耗材名称（例：CMP 研磨液 A 型）。 */
    @Column(name = "cons_name", length = 128, nullable = false)
    private String consName;

    /** 规格型号（例：SL-200L / IC1000-6IN）。 */
    @Column(name = "spec_model", length = 128)
    private String specModel;

    /** 耗材类别（mds.consumable_type 字典：CHEMICAL/SLURRY/GAS/PART/TOOL/OTHER）。 */
    @Column(name = "cons_type", length = 32)
    private String consType;

    /** 计量单位（mds.measure_unit 字典：EA/BOX/SET/PACK/BOTTLE/CAN/ROLL）。 */
    @Column(name = "unit", length = 16)
    private String unit;

    /** 寿命管理方式（mds.consumable_life_type 字典：CYCLE/DAYS/QTY/NONE）。 */
    @Column(name = "life_type", length = 16)
    private String lifeType;

    /** 寿命上限（口径随 life_type：次数/天数/产量；空 = 不限）。 */
    @Column(name = "life_limit", precision = 12, scale = 2)
    private BigDecimal lifeLimit;

    /** 已消耗量（口径随 life_type）。 */
    @Column(name = "life_used", nullable = false, precision = 12, scale = 2)
    private BigDecimal lifeUsed = BigDecimal.ZERO;

    /** 预警阈值（%，寿命/库存到达即预警；判定归运行域）。 */
    @Column(name = "warn_percent")
    private Integer warnPercent;

    /** 当前库存水位（台账快照；流水归物料域）。 */
    @Column(name = "stock_qty", nullable = false, precision = 12, scale = 2)
    private BigDecimal stockQty = BigDecimal.ZERO;

    /** 安全库存水位（低于即预警）。 */
    @Column(name = "min_qty", precision = 12, scale = 2)
    private BigDecimal minQty;

    /** 存放设备主键（cim_equipment.id，MATERIAL 大类：Stocker/OHB/NTB）。 */
    @Column(name = "home_equipment_id", length = 64)
    private String homeEquipmentId;

    /** 供应商名称（采购对齐；可空）。 */
    @Column(name = "vendor", length = 128)
    private String vendor;

    /** 供应商料号（Part No，采购/收货对齐；可空）。 */
    @Column(name = "part_no", length = 64)
    private String partNo;

    /** 有效期（化学品/胶水等入库批次效期，与寿命三轨正交；空 = 不限）。 */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
