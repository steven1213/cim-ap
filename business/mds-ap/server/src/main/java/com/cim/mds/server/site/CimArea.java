package com.cim.mds.server.site;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 区域（Area）—— 层级 {@code Site → Fab → Area → Bay} 的第三层。
 *
 * <p>Area 是工艺大类（刻蚀/光刻/扩散…）的载体，设备台账、AMHS 路径与派工规则
 * 都按 Area 划分作用域；编码在 Fab 内唯一（不同 Fab 可有同名 Area）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_area", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_area_code", columnNames = {"fab_code", "area_code", "deleted"})
})
public class CimArea extends EditLockDefData {

    /** Area 编码（Fab 内唯一，例：ETCH、PHOTO）。 */
    @Column(name = "area_code", length = 64, nullable = false)
    private String areaCode;

    /** 归属 Fab 编码（逻辑引用 {@code cim_fab.fab_code}）。 */
    @Column(name = "fab_code", length = 64, nullable = false)
    private String fabCode;

    /** Area 名称（例：刻蚀区、光刻区）。 */
    @Column(name = "area_name", length = 128, nullable = false)
    private String areaName;

    /** 工艺大类：Etch / Photo / Depo / Diff … */
    @Column(name = "area_type", length = 32)
    private String areaType;

    /** 所属区域组 id（可空逻辑引用 {@code cim_area_group.id}；统计口径分类，见 location-design.md §9.1）。 */
    @Column(name = "area_group_id", length = 64)
    private String areaGroupId;

    /**
     * 默认 Hold 处置部门（V36，Hold Setting；逻辑引用 IAM 组织架构 DEPT 节点 code）。
     * <p>WIP 批在该区被 Hold 时，MES 取此值作处置部门默认值（可被 Hold Code
     * 维度或人工覆盖）；Hold 执行/UnHold 权限归 MES，此处只存配置。</p>
     */
    @Column(name = "hold_dept", length = 32)
    private String holdDept;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
