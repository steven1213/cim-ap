package com.cim.rms.server.ecsv;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * EC/SV 规格（FR-D3，Req 44）：设备常量（EC）/设备状态（SV）的期望规格，
 * 按<b>配方 / 产品</b>双维度（{@code scope}=RECIPE/PRODUCT，{@code refId} 为对应 id）设定。
 */
@Getter
@Setter
@Entity
@Table(name = "ecsv_spec")
public class EcSvSpec extends BaseDefData {

    /** RECIPE / PRODUCT。 */
    @Column(name = "scope", length = 16, nullable = false)
    private String scope;

    /** 配方 id 或产品 id（依 scope）。 */
    @Column(name = "ref_id", length = 64, nullable = false)
    private String refId;

    /** EC / SV。 */
    @Column(name = "ecsv_type", length = 8, nullable = false)
    private String ecsvType;

    /** SV 的标识（EC 时为空）。 */
    @Column(name = "sv_id", length = 64)
    private String svId;

    @Column(name = "param_key", length = 128, nullable = false)
    private String paramKey;

    /** TARGET / RANGE / TOLERANCE（Req 43）。 */
    @Column(name = "spec_type", length = 16, nullable = false)
    private String specType = "TARGET";

    @Column(name = "target_value", length = 128)
    private String targetValue;

    @Column(name = "range_min", length = 64)
    private String rangeMin;

    @Column(name = "range_max", length = 64)
    private String rangeMax;

    @Column(name = "tolerance_pct")
    private Double tolerancePct;

    @Column(name = "tolerance_abs")
    private Double toleranceAbs;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;
}
