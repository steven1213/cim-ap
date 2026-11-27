package com.cim.mds.server.inspection;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 检验标准（InspectionStandard）——「检什么/怎么检/判多严」的静态契约头表（V33）。
 *
 * <p>行业口径：头表声明适用产品分组（mds.product_group 字典）+ 抽样方案
 * （mds.sampling_type 字典）+ AQL 允收水准（mds.aql_level 字典，GB/T 2828.1 /
 * ANSI/ASQ Z1.4 口径）；检验项明细在 {@link CimInspectionItem}（一标准多明细）。
 * 检验执行与判定归 MES/QMS 运行域，主数据只存标准模板——与 EDC 方案「静态契约」同口径。</p>
 *
 * <p>std_code 全局唯一、建后不可改；编辑锁三列与全库主数据同款（明细轻量不带锁）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_inspection_std", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_inspection_std", columnNames = {"std_code", "deleted"})
})
public class CimInspectionStd extends EditLockDefData {

    /** 标准编码（全局唯一，例：INS-CIS-01）。 */
    @Column(name = "std_code", length = 64, nullable = false)
    private String stdCode;

    /** 标准名称（例：CIS 晶圆外观检验标准）。 */
    @Column(name = "std_name", length = 128, nullable = false)
    private String stdName;

    /** 适用产品分组（mds.product_group 字典：CIS/PMIC/LOGIC/…）。 */
    @Column(name = "product_group", length = 32)
    private String productGroup;

    /** 抽样方案（mds.sampling_type 字典：FULL/SAMPLING/FIRST_ARTICLE/PERIODIC）。 */
    @Column(name = "sampling_type", length = 32)
    private String samplingType;

    /** AQL 允收水准（mds.aql_level 字典：0.065~2.5）。 */
    @Column(name = "aql_level", length = 32)
    private String aqlLevel;

    /** 抽样数量（片/批；抽样方案的具体批量换算归运行域）。 */
    @Column(name = "sample_qty")
    private Integer sampleQty;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
