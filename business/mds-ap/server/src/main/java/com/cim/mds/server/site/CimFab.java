package com.cim.mds.server.site;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 晶圆厂（Fab）—— 层级 {@code Site → Fab → Area} 的第二层。
 *
 * <p>归属用 {@code site_code} 逻辑引用（非 FK），与 {@link CimSite} 一致。
 * {@code amhsType} 决定该 Fab 的自动搬运形态（OHT / RGV / NONE），下游 AMHS 建模以此为分叉点。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_fab", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_fab_code", columnNames = {"fab_code", "deleted"})
})
public class CimFab extends BaseDefData {

    /** Fab 编码（唯一，例：F1、F2）。 */
    @Column(name = "fab_code", length = 64, nullable = false)
    private String fabCode;

    /** 归属基地编码（逻辑引用 {@code cim_site.site_code}）。 */
    @Column(name = "site_code", length = 64, nullable = false)
    private String siteCode;

    /** Fab 名称（例：6 英寸一号晶圆厂）。 */
    @Column(name = "fab_name", length = 128, nullable = false)
    private String fabName;

    /** 工厂类型：6 寸 / 8 寸 / 12 寸 / Assembly 封装。 */
    @Column(name = "fab_type", length = 32)
    private String fabType;

    /** AMHS 类型：OHT / RGV / NONE（无自动搬运）。 */
    @Column(name = "amhs_type", length = 32)
    private String amhsType;

    /** Fab 整体洁净等级（例：Class 100）。 */
    @Column(name = "clean_grade", length = 32)
    private String cleanGrade;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
