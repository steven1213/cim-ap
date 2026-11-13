package com.cim.mds.server.site;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 制程技术（Technology）——产品之上的技术节点分类（产品管理域）。
 *
 * <p>行业参照系（SiView SM）：Technology → Product → Route → Operation。
 * tech_code <b>全局唯一</b>（技术节点是产品属性、跨厂复用，不按 Fab 隔离）。
 * 将来 Product 落地时按 {@code tech_code} 逻辑引用本表；删除守门（被产品引用不可删）
 * 届时补。Manufacturing Layer 不建模（Route 的分段属性，归工艺路线域）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_technology", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_technology_code", columnNames = {"tech_code", "deleted"})
})
public class CimTechnology extends EditLockDefData {

    /** 技术编码（全局唯一，例：TECH-01）。 */
    @Column(name = "tech_code", length = 64, nullable = false)
    private String techCode;

    /** 技术名称（例：CMOS Logic）。 */
    @Column(name = "tech_name", length = 128, nullable = false)
    private String techName;

    /** 节点值（例：0.35um、1.0um；可空，便于排序与筛选）。 */
    @Column(name = "tech_node", length = 32)
    private String techNode;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
