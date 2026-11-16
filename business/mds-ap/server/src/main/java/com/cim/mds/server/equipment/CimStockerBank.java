package com.cim.mds.server.equipment;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Stocker 存储区（StockerBank）——Stocker 内部逻辑存储分区。
 *
 * <p>行业口径（SEMI E88 Stocker SEM 5.2.30）：标准术语是 zone（ZoneName/ZoneType/
 * ZoneSize/ZoneCapacity），「Bank」是 MCS 厂商对 zone 的本地命名，本表即 zone 的
 * 主数据落点。层级 {@code Stocker(cim_equipment) → Bank(本表) → Shelf 单库位}——
 * Shelf 不建模（实务口径：设备黑箱 + 区级容量即可）；剩余容量是运行时状态归 MCS，
 * 主数据只存 capacity（总储位数）。bank_type 挂业务用途字典 mds.stocker_bank_type。</p>
 *
 * <p>bank_code 在所属设备内唯一（E88 ZoneName 作用域 = 单台 Stocker）；
 * 编辑锁三列与全库主数据同款。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_stocker_bank", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_stocker_bank", columnNames = {"equipment_id", "bank_code", "deleted"})
})
public class CimStockerBank extends EditLockDefData {

    /** 所属设备主键（cim_equipment.id，MATERIAL 大类：Stocker/OHB/NTB 等存储设备）。 */
    @Column(name = "equipment_id", length = 64, nullable = false)
    private String equipmentId;

    /** 存储区编码（设备内唯一，例：BK-A01）。 */
    @Column(name = "bank_code", length = 64, nullable = false)
    private String bankCode;

    /** 存储区名称（例：一号库·原料区）。 */
    @Column(name = "bank_name", length = 128, nullable = false)
    private String bankName;

    /** 业务用途类型（mds.stocker_bank_type 字典：RAW/WIP/FG/SCRAP/CONTROL/RECYCLE/GENERAL）。 */
    @Column(name = "bank_type", length = 32)
    private String bankType;

    /** 总储位数（E88 ZoneSize 口径；剩余容量 ZoneCapacity 归 MCS 运行时）。 */
    @Column(name = "capacity")
    private Integer capacity;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
