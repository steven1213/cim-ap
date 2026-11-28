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
 * 消耗品规格（Consumable Spec）——带过程控制约束的物料静态档案（V34 台账 / V37 规格升级）。
 *
 * <p>MDS 边界 = 定义「是什么」（规格档案）与「按什么标准消耗」（消耗品 BOM，V38）；
 * 库存扣减/采购/齐套/上料阻断归 MES/ERP 运行时，本表只存基准与防呆约束。</p>
 *
 * <p>行业口径：编码/规格型号/类别/计量单位 + 过程控制属性（保质期/LDU/存储回温/
 * 公差/密度效能因子）+ 治理属性（替代组/生命周期三态/批次标记/耐用品边界）。
 * 寿命管理三轨（mds.consumable_life_type 字典）：CYCLE/DAYS/QTY；life_limit = 寿命上限、
 * life_used = 已消耗快照、warn_percent = 预警阈值（%），到期判定与预警触发归运行域。</p>
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

    /** 耗材类别（mds.consumable_type 字典：GAS/CHEMICAL/SLURRY/TARGET/PART/PACKAGING/OTHER；TOOL 移除，耐用工具归 equipment 域）。 */
    @Column(name = "cons_type", length = 32)
    private String consType;

    /** 物料边界：CONSUMABLE 消耗品 / DURABLE 耐用品——DURABLE 不得被消耗品 BOM 引用。 */
    @Column(name = "material_kind", nullable = false, length = 16)
    private String materialKind = KIND_CONSUMABLE;

    /** 生命周期：ACTIVE 在售 / END_OF_SALE 停售 / OBSOLETE 淘汰（不得被新 BOM 引用）。 */
    @Column(name = "lifecycle", nullable = false, length = 16)
    private String lifecycle = LC_ACTIVE;

    /** 保质期（天，自生产日期起；空 = 不限；到期判定归运行时）。 */
    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    /** 开封后使用时限（Pot Life，小时；超期阻断消耗，判定归运行时）。 */
    @Column(name = "ldu_hours")
    private Integer lduHours;

    /** 存储温度要求（文本区间，例：2-8°C / -20°C）。 */
    @Column(name = "storage_temp", length = 32)
    private String storageTemp;

    /** 回温/解冻时间（小时；上料前条件校验基准）。 */
    @Column(name = "thaw_time")
    private Integer thawTime;

    /** 称量/配比容差（±%，例 5.00 = ±5%）。 */
    @Column(name = "tolerance_pct", precision = 5, scale = 2)
    private BigDecimal tolerancePct;

    /** 密度/效能因子（体积↔重量换算与配方修正；空 = 1:1）。 */
    @Column(name = "density_factor", precision = 12, scale = 6)
    private BigDecimal densityFactor;

    /** 替代组码（同组规格可互相替代；BOM 行按组引用，选择策略在运行时）。 */
    @Column(name = "substitute_group", length = 64)
    private String substituteGroup;

    /** 批次追溯标记（关键化学品/靶材必开；实例化批次跟踪归 MES）。 */
    @Column(name = "lot_control", nullable = false)
    private Boolean lotControl = Boolean.FALSE;

    /** 生命周期常量：ACTIVE 在售 / END_OF_SALE 停售 / OBSOLETE 淘汰。 */
    public static final String LC_ACTIVE = "ACTIVE";
    public static final String LC_END_OF_SALE = "END_OF_SALE";
    public static final String LC_OBSOLETE = "OBSOLETE";
    /** 物料边界常量：CONSUMABLE 消耗品 / DURABLE 耐用品。 */
    public static final String KIND_CONSUMABLE = "CONSUMABLE";
    public static final String KIND_DURABLE = "DURABLE";

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
