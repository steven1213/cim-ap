package com.cim.rms.server.spec;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 规格模板条目（FR-S2）：模板内单条参数规格。 */
@Getter
@Setter
@Entity
@Table(name = "spec_template_item")
public class SpecTemplateItem extends BaseDefData {

    @Column(name = "template_id", length = 64, nullable = false)
    private String templateId;

    @Column(name = "param_key", length = 128, nullable = false)
    private String paramKey;

    @Column(name = "param_name", length = 128)
    private String paramName;

    @Column(name = "spec_type", length = 16, nullable = false)
    private String specType = SpecType.TARGET.name();

    @Column(name = "data_type", length = 16, nullable = false)
    private String dataType = "NUMBER";

    @Column(name = "target_value", length = 128)
    private String targetValue;

    @Column(name = "range_min", length = 64)
    private String rangeMin;

    @Column(name = "range_max", length = 64)
    private String rangeMax;

    @Column(name = "list_values", length = 1024)
    private String listValues;

    @Column(name = "tolerance_pct")
    private Double tolerancePct;

    @Column(name = "tolerance_abs")
    private Double toleranceAbs;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_no", nullable = false)
    private int sortNo = 0;
}
