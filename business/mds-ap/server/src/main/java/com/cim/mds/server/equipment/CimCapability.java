package com.cim.mds.server.equipment;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备能力（Capability）——能力字典（ISA-95 Present Capability 的声明侧）。
 *
 * <p>能力回答「能不能做」（如 6 英寸加工、E84 接口、双端口搬运），recipe 回答「怎么做」
 * ——两者不混。设备↔能力关联见 {@link CimEquipmentCapability}；工序侧的
 * Request Capability 匹配归 MES 域，本库不管。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_capability", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_capability_code", columnNames = {"capability_code", "deleted"})
})
public class CimCapability extends EditLockDefData {

    /** 能力编码（全局唯一，例：CAP-E84）。 */
    @Column(name = "capability_code", length = 64, nullable = false)
    private String capabilityCode;

    /** 能力名称（例：E84 接口能力）。 */
    @Column(name = "capability_name", length = 128, nullable = false)
    private String capabilityName;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
