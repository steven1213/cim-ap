package com.cim.mds.server.reticle;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 光罩（Reticle/Photo Mask）——光刻关键耗材级主数据（V32）。
 *
 * <p>工序（CimOperation.reticleRef）、流程步骤（CimFlowStep.reticleRef）、产品覆盖
 * （CimProductFlow.reticleRef）按 reticle_code 字符串引用本表——写入侧校验存在性、
 * 光罩删除反向引用拒删，引用闭环。寿命双轨：使用次数（usage_count/usage_limit）+
 * 有效期（expiry_date）；到期/报废判定归运行域。</p>
 *
 * <p>reticle_code 全局唯一、建后不可改；编辑锁三列与全库主数据同款。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_reticle", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_reticle", columnNames = {"reticle_code", "deleted"})
})
public class CimReticle extends EditLockDefData {

    /** 光罩编码（全局唯一，例：RT-LT-001）。 */
    @Column(name = "reticle_code", length = 64, nullable = false)
    private String reticleCode;

    /** 光罩名称（例：光罩 LT 层 01 号）。 */
    @Column(name = "reticle_name", length = 128, nullable = false)
    private String reticleName;

    /** 光刻层（例：LT/VT/MT/AA；层名随工艺路线命名，自由文本不字典化）。 */
    @Column(name = "layer", length = 64)
    private String layer;

    /** 台账状态（mds.reticle_status 字典：IN_STOCK/ACTIVE/DAMAGED/EXPIRED/SCRAPPED）。 */
    @Column(name = "status", length = 32)
    private String status;

    /** 存放设备主键（cim_equipment.id，MATERIAL 大类：Stocker/OHB/NTB）。 */
    @Column(name = "home_equipment_id", length = 64)
    private String homeEquipmentId;

    /** 已使用次数。 */
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    /** 寿命上限（使用次数；空 = 不限）。 */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    /** 有效期（日期；空 = 不限）。 */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
