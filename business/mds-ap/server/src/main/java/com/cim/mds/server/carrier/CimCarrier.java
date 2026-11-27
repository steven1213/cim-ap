package com.cim.mds.server.carrier;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 载具（Carrier/Cassette）——晶圆/光罩在 AMHS 全链中的物理载体主数据（V31）。
 *
 * <p>行业口径（SEMI E87 CMS Carrier）：载具号是 MCS/MES 全链追踪的主键口径，本表即
 * 主数据落点：号码/类型/状态/所属库/内容物绑定。E87 的运行时状态机
 * （AccessingState/CSPort）归 MCS，主数据只存台账级状态（mds.carrier_status 字典）。
 * 内容物绑定 current_lot_id 是 MES 批次号的字符串引用（批主数据在 MES，不 FK）。</p>
 *
 * <p>carrier_code 全局唯一、建后不可改（全库跨域引用口径）；编辑锁三列与全库主数据同款。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_carrier", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_carrier", columnNames = {"carrier_code", "deleted"})
})
public class CimCarrier extends EditLockDefData {

    /** 载具编码（全局唯一，例：C-0001 / RC-0001）。 */
    @Column(name = "carrier_code", length = 64, nullable = false)
    private String carrierCode;

    /** 载具名称（例：一号库·标准花篮 01）。 */
    @Column(name = "carrier_name", length = 128, nullable = false)
    private String carrierName;

    /** 载具类型（mds.carrier_type 字典：CASSETTE/FOUP/RETICLE_BOX/WAFER_BOX/OTHER）。 */
    @Column(name = "carrier_type", length = 32)
    private String carrierType;

    /** 适用晶圆尺寸（英寸数字串，mds.wafer_size 字典："6"/"8"/"12"；可空 = 未限定）。 */
    @Column(name = "wafer_size", length = 16)
    private String waferSize;

    /** 额定槽数（E87 口径：FOUP=25 / 6 寸花篮=13 / 8 寸花篮=25；可空）。 */
    @Column(name = "capacity")
    private Integer capacity;

    /** 台账状态（mds.carrier_status 字典：IN_STOCK/IN_USE/IN_CLEANING/MAINTENANCE/SCRAPPED）。 */
    @Column(name = "status", length = 32)
    private String status;

    /** 归属存储设备主键（cim_equipment.id，MATERIAL 大类：Stocker/OHB/NTB）。 */
    @Column(name = "home_equipment_id", length = 64)
    private String homeEquipmentId;

    /** 内容物绑定（MES 批次号字符串引用；空 = 空载具）。 */
    @Column(name = "current_lot_id", length = 64)
    private String currentLotId;

    /** 当前装载晶圆数（空载具为空/0）。 */
    @Column(name = "wafer_qty")
    private Integer waferQty;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
