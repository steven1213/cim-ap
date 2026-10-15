package com.cim.rms.server.template;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Body 模板条目（强制键定义）：required=缺失即违规；defaultValue 供骨架填充与向导提示。
 */
@Getter
@Setter
@Entity
@Table(name = "rms_body_template_item")
public class BodyTemplateItem extends BaseDefData {

    @Column(name = "template_id", length = 64, nullable = false)
    private String templateId;

    @Column(name = "param_key", length = 128, nullable = false)
    private String paramKey;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "default_value", length = 256)
    private String defaultValue;

    @Column(name = "unit", length = 64)
    private String unit;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;
}
