package com.cim.mds.server.inspection;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 检验项（InspectionItem）——检验标准的明细行（V33）。
 *
 * <p>一标准多明细，页内行编辑全量替换维护；(std_id, item_code) 唯一。缺陷等级挂
 * mds.defect_level 字典（CRITICAL/MAJOR/MINOR）。轻量表不带编辑锁三列
 * （与 cim_bank_transfer_rule 同口径）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_inspection_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_inspection_item", columnNames = {"std_id", "item_code", "deleted"})
})
public class CimInspectionItem extends BaseDefData {

    /** 所属标准主键（cim_inspection_std.id）。 */
    @Column(name = "std_id", length = 64, nullable = false)
    private String stdId;

    /** 检验项编码（标准内唯一，例：CHK-SCRATCH）。 */
    @Column(name = "item_code", length = 64, nullable = false)
    private String itemCode;

    /** 检验项名称（例：表面划伤）。 */
    @Column(name = "item_name", length = 128, nullable = false)
    private String itemName;

    /** 判定规格（例：不允许长度 > 2mm 的线性划伤）。 */
    @Column(name = "spec", length = 512)
    private String spec;

    /** 检验方法（例：目检/自动检查）。 */
    @Column(name = "method", length = 255)
    private String method;

    /** 量具/设备（例：40x 显微镜）。 */
    @Column(name = "instrument", length = 128)
    private String instrument;

    /** 缺陷等级（mds.defect_level 字典：CRITICAL/MAJOR/MINOR）。 */
    @Column(name = "defect_level", length = 32)
    private String defectLevel;

    /** 排序号。 */
    @Column(name = "sort_no", nullable = false)
    private Integer sortNo = 0;
}
