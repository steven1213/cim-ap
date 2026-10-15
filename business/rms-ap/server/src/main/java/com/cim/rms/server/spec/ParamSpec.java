package com.cim.rms.server.spec;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 参数规格（FR-S1，Req 29/34/36）：参数的一等公民建模。
 *
 * <p>挂在 Recipe 上（一个配方一套规格，可随模板应用/继承 Active 版本规格）；{@code enabled}
 * 即「是否参与比对」开关（Req 34，FR-S4）；{@code tolerancePct}/{@code toleranceAbs}
 * 为双轨容差（Req 36）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "param_spec")
public class ParamSpec extends BaseDefData {

    @Column(name = "recipe_id", length = 64, nullable = false)
    private String recipeId;

    /** 参数路径/键（如 {@code CHAMBER.TEMP}）。 */
    @Column(name = "param_key", length = 128, nullable = false)
    private String paramKey;

    @Column(name = "param_name", length = 128)
    private String paramName;

    @Column(name = "spec_type", length = 16, nullable = false)
    private String specType = SpecType.TARGET.name();

    /** 数据类型：NUMBER / TEXT / BOOL（决定数值语义或字符串比较）。 */
    @Column(name = "data_type", length = 16, nullable = false)
    private String dataType = "NUMBER";

    @Column(name = "target_value", length = 128)
    private String targetValue;

    @Column(name = "range_min", length = 64)
    private String rangeMin;

    @Column(name = "range_max", length = 64)
    private String rangeMax;

    /** 枚举列表（逗号分隔，LIST_IN 使用）。 */
    @Column(name = "list_values", length = 1024)
    private String listValues;

    @Column(name = "tolerance_pct")
    private Double tolerancePct;

    @Column(name = "tolerance_abs")
    private Double toleranceAbs;

    @Column(name = "unit", length = 32)
    private String unit;

    /** 是否参与比对（Req 34，FR-S4）。 */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** 来源模板编码（应用模板后回填，可空；便于追溯，Req 35）。 */
    @Column(name = "template_code", length = 64)
    private String templateCode;
}
