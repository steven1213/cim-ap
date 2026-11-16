package com.cim.mds.server.equipment;

import com.cim.core.model.BaseDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Bank 间转运规则（BankTransferRule）——「允许从哪个 Bank 搬到哪个 Bank」的路由表。
 *
 * <p>行业口径（SEMI E88 6.1.1 / 6.1.2）：Stocker 侧不选目的地，<b>目的地分配与容量
 * 校验责任都在 Host</b>——本表即 Host 侧路由主数据的落点；MCS 发转运指令前按
 * from→to 查本表守门，再校验目的 Bank 剩余容量。from≠to 守门；跨 Stocker 允许
 * （跨库=换巷道，物理上必经 OHT/OHS，属正常路径）。priority 沿 E88 Priority 1-99
 * 口径（99 最高），供派工排序。</p>
 *
 * <p>轻量表不带编辑锁三列（与 cim_equipment_capability 同口径）；
 * from/to 唯一对（软删口径与全库一致）。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_bank_transfer_rule", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_bank_transfer_rule", columnNames = {"from_bank_id", "to_bank_id", "deleted"})
})
public class CimBankTransferRule extends BaseDefData {

    /** 源 Bank 主键（cim_stocker_bank.id）。 */
    @Column(name = "from_bank_id", length = 64, nullable = false)
    private String fromBankId;

    /** 目的 Bank 主键（cim_stocker_bank.id，可与源不同 Stocker）。 */
    @Column(name = "to_bank_id", length = 64, nullable = false)
    private String toBankId;

    /** 优先级（E88 Priority 口径 1-99，99 最高；派工排序用，可空）。 */
    @Column(name = "priority")
    private Integer priority;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
