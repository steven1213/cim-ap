package com.cim.mds.server.site;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 产品（Product）——产品管理域主数据（SiView：Technology → Product → Route）。
 *
 * <p>行业通用口径裁剪（对标 Auto3/SiView ProductSpec）：身份 + 分类 + 技术引用 +
 * 启停；Material Control / Process / Plan / CDA / Bonding / MES Roles 不建模
 * （归 MES 或平台层）。product_code <b>全局唯一</b>；tech_code 逻辑引用
 * {@link CimTechnology}（不做 FK），反向守门：被引用的制程技术不可删。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_product", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_product_code", columnNames = {"product_code", "deleted"})
})
public class CimProduct extends EditLockDefData {

    /** 产品编码（全局唯一，例：PROD-01）。 */
    @Column(name = "product_code", length = 64, nullable = false)
    private String productCode;

    /** 产品名称/描述（例：0.35um CIS 图像传感器）。 */
    @Column(name = "product_name", length = 128, nullable = false)
    private String productName;

    /** 所属制程技术编码（逻辑引用 cim_technology.tech_code，可空）。 */
    @Column(name = "tech_code", length = 64)
    private String techCode;

    /** 产品用途类型（5 值枚举，锚 SEMI E10/E79 产品片 vs 非产品片口径）：
     * PRODUCTION=量产 / ENGINEERING=工程 / EXPERIMENT=实验 / TEST=测试验证 / NPW=非产品陪跑。 */
    @Column(name = "usage_type", length = 16, nullable = false)
    private String usageType = "PRODUCTION";

    /** NPW 二级细分（仅 usageType=NPW 时必填，服务层守门；其余用途必须为空）：
     * MONITOR=控挡 / DUMMY=填充 / SEASON=暖机 / LITHO=光刻域辅材（SEMI M38 Lithography） / GENERIC=兜底。 */
    @Column(name = "npw_type", length = 16)
    private String npwType;

    /** 产品分组码（轻实现：varchar，分组管理需求出现再升级实体）。 */
    @Column(name = "product_group", length = 64)
    private String productGroup;

    /** 晶圆类型（例：外延片、P 型衬底；可空）。 */
    @Column(name = "wafer_type", length = 64)
    private String waferType;

    /** 业务责任人账号（可空，不关联组织架构）。 */
    @Column(name = "owner", length = 64)
    private String owner;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
