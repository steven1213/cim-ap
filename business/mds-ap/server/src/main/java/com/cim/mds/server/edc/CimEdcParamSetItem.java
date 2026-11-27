package com.cim.mds.server.edc;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * EDC 参数集成员（V28）——参数集 × 参数模板的打包明细。
 *
 * <p>attr_key 逻辑引用 cim_param_def（服务层守门存在且启用）；同集内参数唯一。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_edc_param_set_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_edc_param_set_item", columnNames = {"set_id", "attr_key", "deleted"})
})
public class CimEdcParamSetItem extends BaseDefData {

    /** 所属参数集 id（物理引用）。 */
    @Column(name = "set_id", length = 64, nullable = false)
    private String setId;

    /** 参数键（逻辑引用 cim_param_def.attr_key，建后不可改）。 */
    @Column(name = "attr_key", length = 64, nullable = false)
    private String attrKey;

    /** 展示顺序（小者在前）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
