package com.cim.mds.server.edc;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import com.cim.core.model.BaseDefData;

/**
 * EDC 报告模板成员（V28）——模板内的单行「SVID → 参数」映射；同一 SVID 在
 * 同一模板内唯一，attr_key 逻辑引用 cim_param_def（服务层守门存在性）。
 */
@Getter
@Setter
@Entity
@Table(name = "cim_edc_report_tpl_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_edc_report_tpl_item", columnNames = {"tpl_id", "svid", "deleted"})
})
public class CimEdcReportTplItem extends BaseDefData {

    /** 所属模板 id（逻辑引用 cim_edc_report_tpl.id）。 */
    @Column(name = "tpl_id", nullable = false)
    private String tplId;

    /** 设备状态变量 ID（SEMI E5 SVID，模板内唯一）。 */
    @Column(name = "svid", nullable = false)
    private Integer svid;

    /** MDS 参数键（逻辑引用 cim_param_def.attr_key）。 */
    @Column(name = "attr_key", length = 64, nullable = false)
    private String attrKey;

    /** 排序号（报告成员展示顺序）。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
