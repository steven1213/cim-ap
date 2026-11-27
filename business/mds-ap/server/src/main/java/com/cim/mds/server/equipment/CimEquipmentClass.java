package com.cim.mds.server.equipment;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 机型模板（EquipmentClass）——设备域的 Class 层主数据（ISA-95 EquipmentClass 口径）。
 *
 * <p>v2（2026-11-24 设备建模评审定稿）取代旧 EquipmentType：除三级分类
 * （{@code kind_code} 8 大类 → {@code equip_class_code} 细类 → {@code class_code} 机型）外，
 * 新增<b>默认约束</b>（默认片径/载具类型/batch/标准产能）——机台实例新建时带入、
 * 可覆盖，是「模板先行 → 派生实例」动线的数据基础。</p>
 *
 * <p>6/8/12 寸通用性约定：<b>不按寸硬编码</b>，片径/载具用 CSV 多值表达
 * （{@code 150} / {@code 200,300}），一台 6 寸机即 {@code 150}，12 寸兼容机 {@code 200,300}。</p>
 *
 * <p>列以平台基类为准（雪花 id、逻辑删除、审计 + tenant_id、编辑锁三列）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_equipment_class", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_equipment_class_code", columnNames = {"class_code", "deleted"})
})
public class CimEquipmentClass extends EditLockDefData {

    /** 机型编码（全局唯一，例：ET-OHT）。建后不可改——机台按 code 引用。 */
    @Column(name = "class_code", length = 64, nullable = false)
    private String classCode;

    /** 机型名称（例：OHT 天车）。 */
    @Column(name = "class_name", length = 128, nullable = false)
    private String className;

    /** 设备大类：PROCESS/METRO/TEST/ASSY/MATERIAL/FACILITY/TOOL/OTHER（页面归属依据）。 */
    @Column(name = "kind_code", length = 16, nullable = false)
    private String kindCode;

    /** 设备细类（mds.equip_class 字典，须从属于 kindCode，可选）。 */
    @Column(name = "equip_class_code", length = 16)
    private String equipClassCode;

    /** 默认支持片径（CSV，mm：150/200/300；实例可覆盖）。 */
    @Column(name = "default_wafer_sizes", length = 64)
    private String defaultWaferSizes;

    /** 默认接受载具类型（CSV：CASSETTE/FOUP/FOSB/MAGAZINE；实例可覆盖）。 */
    @Column(name = "default_carrier_types", length = 128)
    private String defaultCarrierTypes;

    /** 默认最小批（实例可覆盖）。 */
    @Column(name = "default_batch_min")
    private Integer defaultBatchMin;

    /** 默认最大批（实例可覆盖）。 */
    @Column(name = "default_batch_max")
    private Integer defaultBatchMax;

    /** 默认标准产能（片/小时；实例可覆盖）。 */
    @Column(name = "default_standard_wph", precision = 10, scale = 2)
    private BigDecimal defaultStandardWph;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用：停用后不可被机台新引用，存量数据仍可查。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
