package com.cim.mds.server.consumable;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 消耗品 BOM 明细行——一行定义一个消耗品的消耗规则（V38）。
 *
 * <p>全量替换维护（页内行编辑一次提交，同检验标准 V33 模式），轻量不带锁。
 * 消耗类型两轨（mds.cons_consume_type 字典）：FIXED 固定用量（每基准一次消耗
 * std_qty）/ VARIABLE 变量消耗（param_key 每单位用量 × 运行时工艺参数值；
 * 公式计算归 EAP/运行时，MDS 只保证引用链确定性）。</p>
 *
 * <p>守门：spec 必须 ACTIVE 且 material_kind=CONSUMABLE；VARIABLE 行 param_key
 * 必须在 cim_param_def 注册且启用；同头同 spec 唯一。行内替代：substitute_group
 * + sub_priority + expiry_policy（FIFO/FEFO，MDS 只存策略标记，运行时执行）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_cons_bom_line", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_cons_bom_line", columnNames = {"bom_id", "spec_id", "deleted"})
})
public class CimConsBomLine extends BaseDefData {

    /** 固定用量（每基准一次消耗 std_qty，如每批次固定消耗一片耗材件）。 */
    public static final String CT_FIXED = "FIXED";
    /** 变量消耗（param_key 每单位用量 × 运行时工艺参数值，如时间×流量算气体）。 */
    public static final String CT_VARIABLE = "VARIABLE";

    /** 所属 BOM 头 id。 */
    @Column(name = "bom_id", length = 64, nullable = false)
    private String bomId;

    /** 消耗品规格 id（cim_consumable，须 ACTIVE 且 CONSUMABLE）。 */
    @Column(name = "spec_id", length = 64, nullable = false)
    private String specId;

    /** 消耗类型（FIXED/VARIABLE）。 */
    @Column(name = "consume_type", length = 16, nullable = false)
    private String consumeType = CT_FIXED;

    /** 用量基准（mds.cons_basis 字典：PER_WAFER 每片/PER_LOT 每批/PER_RUN 每次）。 */
    @Column(name = "basis", length = 16, nullable = false)
    private String basis = "PER_LOT";

    /** 标准用量（配合 basis 解释；运行时与 EDC 实际消耗比对出 OOC）。 */
    @Column(name = "std_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal stdQty;

    /** 容差 ±%（可空；允许的实际消耗偏离范围）。 */
    @Column(name = "tolerance_pct", precision = 6, scale = 3)
    private BigDecimal tolerancePct;

    /** 损耗因子（可空；耗材件/靶材寿命模型补充触发基准值，触发动作归运行时）。 */
    @Column(name = "loss_factor", precision = 8, scale = 4)
    private BigDecimal lossFactor;

    /** 变量消耗参数键（VARIABLE 时必填；须在 cim_param_def 注册且启用）。 */
    @Column(name = "param_key", length = 64)
    private String paramKey;

    /** 每参数单位用量（VARIABLE 时配合 param_key；实际量 = 参数值 × 本值）。 */
    @Column(name = "param_qty_per_unit", precision = 18, scale = 6)
    private BigDecimal paramQtyPerUnit;

    /** 替代组码（可空；同组规格可互相替代，BOM 行按组选择实际物料）。 */
    @Column(name = "substitute_group", length = 64)
    private String substituteGroup;

    /** 替代优先级（可空；小者优先，同组多行时供运行时排序）。 */
    @Column(name = "sub_priority")
    private Integer subPriority;

    /** 失效策略标记（mds.cons_expiry_policy 字典：FIFO/FEFO，运行时执行）。 */
    @Column(name = "expiry_policy", length = 16)
    private String expiryPolicy;

    /** 显示顺序。 */
    @Column(name = "sort_no")
    private Integer sortNo;

    /** 备注。 */
    @Column(name = "note", length = 512)
    private String note;
}
