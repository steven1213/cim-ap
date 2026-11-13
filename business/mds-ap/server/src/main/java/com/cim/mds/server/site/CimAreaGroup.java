package com.cim.mds.server.site;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 区域组（AreaGroup）—— Area 的统计口径分类（location-design.md §9.1）。
 *
 * <p>用途=工艺分段汇总（前段/后段等），是「树内虚拟分节 + 表单属性 + 筛选维度」，
 * <b>不是位置树的正式一层</b>：不改 Area 的物理父子关系（Area 的父永远是 Fab）。
 * 编码在 Fab 内唯一；组删除前须先移出组内 Area。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_area_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_area_group_code", columnNames = {"fab_code", "group_code", "deleted"})
})
public class CimAreaGroup extends EditLockDefData {

    /** 区域组编码（Fab 内唯一，例：FEOL、BEOL）。 */
    @Column(name = "group_code", length = 64, nullable = false)
    private String groupCode;

    /** 归属 Fab 编码（逻辑引用 {@code cim_fab.fab_code}）。 */
    @Column(name = "fab_code", length = 64, nullable = false)
    private String fabCode;

    /** 区域组名称（例：前段工艺、后段工艺）。 */
    @Column(name = "group_name", length = 128, nullable = false)
    private String groupName;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用（停用后 Area 不可新入组）。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
