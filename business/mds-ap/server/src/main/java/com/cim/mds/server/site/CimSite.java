package com.cim.mds.server.site;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 基地（Site）—— MDS 场地层级 {@code Site → Fab → Area → Bay → Module} 的根节点。
 *
 * <p>层级之间用<b>编码逻辑引用</b>（{@code site_code} / {@code fab_code} / {@code area_code}），
 * 不建 DB 外键：MDS 各域可独立演进与分库，物理 FK 会形成跨域强耦合
 * （见 docs/business/mds-ap/server/00-blueprint.md §11）。</p>
 *
 * <p>列以平台基类 {@link BaseDefData} 为准（{@code id} 应用层雪花、{@code deleted} 逻辑删除、
 * 审计四列 + {@code tenant_id} 由 {@code AuditableEntityListener} 自动填充）；
 * MDS 早期设计稿里的 {@code bigint id} / {@code deleted_at} 不采用——与 Flyway
 * {@code ddl-auto: validate} 及全平台约定冲突。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_site", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_site_code", columnNames = {"site_code", "deleted"})
})
public class CimSite extends BaseDefData {

    /** 基地编码（唯一，例：CD-SITE、SH-SITE）。 */
    @Column(name = "site_code", length = 64, nullable = false)
    private String siteCode;

    /** 基地名称（例：成都园区）。 */
    @Column(name = "site_name", length = 128, nullable = false)
    private String siteName;

    /** 详细地址。 */
    @Column(name = "address", length = 256)
    private String address;

    /** 基地负责人（IAM 账号或姓名）。 */
    @Column(name = "contact_person", length = 64)
    private String contactPerson;

    /** 联系电话。 */
    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用：停用后不可被 Fab 新引用，存量数据仍可查。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
