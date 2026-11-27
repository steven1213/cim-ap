package com.cim.mds.server.edc;

import java.math.BigDecimal;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * EDC 方案明细（V28）= 采集项 + Measurement Spec（量测规格合一挂行）。
 *
 * <p>行业红线：规格限挂在「参数 × 产品 × 工序」上而非参数本体——方案已绑
 * 产品+工序，故 LSL/USL/Target 与越限响应动作挂本表实现同参数差异化管控。
 * 规格限是静态主数据（客户/设计规格，OOS 依据）；控制限由 SPC 引擎动态计算，
 * 绝不落本表（OOC 归 SPC 运行域）。采样规则定义采集频率/触发条件（按批/按片/
 * 按时间/按事件），不含 wafer 级 TOP+N 等位选策略（属 MES 采样计划域）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_edc_plan_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_edc_plan_item", columnNames = {"plan_id", "attr_key", "deleted"})
})
public class CimEdcPlanItem extends BaseDefData {

    // ---------------- 采集方式 ----------------
    /** 设备自动上报（经 SVID/CEID 映射解析）。 */
    public static final String CM_AUTO = "AUTO";
    /** 人工录入。 */
    public static final String CM_MANUAL = "MANUAL";
    /** 半自动（扫码/文件导入）。 */
    public static final String CM_SEMI = "SEMI";

    // ---------------- 采样规则 ----------------
    /** 按批次采样。 */
    public static final String SR_BY_LOT = "BY_LOT";
    /** 按晶圆采样。 */
    public static final String SR_BY_WAFER = "BY_WAFER";
    /** 按时间间隔采样（interval_minutes 生效）。 */
    public static final String SR_BY_TIME = "BY_TIME";
    /** 由设备事件触发（配合 CEID）。 */
    public static final String SR_BY_EVENT = "BY_EVENT";

    // ---------------- 越限响应动作 ----------------
    /** 仅记录不处置。 */
    public static final String OA_NONE = "NONE";
    /** 触发 Hold（MES 运行域执行）。 */
    public static final String OA_HOLD = "HOLD";
    /** 通知 OCAP。 */
    public static final String OA_NOTIFY = "NOTIFY";

    /** 所属方案 id（物理引用）。 */
    @Column(name = "plan_id", length = 64, nullable = false)
    private String planId;

    /** 参数键（逻辑引用 cim_param_def.attr_key）。 */
    @Column(name = "attr_key", length = 64, nullable = false)
    private String attrKey;

    /** 采集方式：AUTO / MANUAL / SEMI。 */
    @Column(name = "collect_mode", length = 16, nullable = false)
    private String collectMode = CM_AUTO;

    /** 采样规则：BY_LOT / BY_WAFER / BY_TIME / BY_EVENT。 */
    @Column(name = "sample_rule", length = 16, nullable = false)
    private String sampleRule = SR_BY_LOT;

    /** 时间间隔（分钟；sample_rule=BY_TIME 时必填，服务层守门）。 */
    @Column(name = "interval_minutes")
    private Integer intervalMinutes;

    /** 是否必填（采集执行侧缺值 → 异常，运行域处置）。 */
    @Column(name = "required", nullable = false)
    private Boolean required = Boolean.FALSE;

    /** 目标值（量测规格）。 */
    @Column(name = "target", precision = 18, scale = 6)
    private BigDecimal target;

    /** 规格下限（OOS 依据，可空 = 不设下限）。 */
    @Column(name = "lsl", precision = 18, scale = 6)
    private BigDecimal lsl;

    /** 规格上限（OOS 依据，可空 = 不设上限）。 */
    @Column(name = "usl", precision = 18, scale = 6)
    private BigDecimal usl;

    /** 越限响应动作：NONE / HOLD / NOTIFY（运行域执行）。 */
    @Column(name = "ooc_action", length = 16, nullable = false)
    private String oocAction = OA_NONE;

    /** 展示顺序（小者在前）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
