package com.cim.mds.server.equipment;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 机台（Equipment）——设备域的实例主数据（一张表，按机型大类 kind 分页视图）。
 *
 * <p>v2（2026-11-24 设备建模评审定稿，对标 ISA-95 与行业 CIM 设备页签）：</p>
 * <ul>
 *   <li>{@code class_code} 引用机型模板 {@link CimEquipmentClass}（逻辑引用，非 FK）；
 *       约束字段（片径/载具/batch/产能）新建时从模板默认值带入、可覆盖——
 *       <b>覆盖后与模板解耦</b>，实例改模板不影响存量机台。</li>
 *   <li>{@code owner_user} 设备责任人（IAM username 逻辑引用；目录接入前允许手填工号/姓名）。</li>
 *   <li>{@code control_mode} 通信控制模式（SEMI E30：ONLINE_REMOTE/ONLINE_LOCAL/LOCAL/OFFLINE，
 *       字典 {@code mds.equip_control_mode}）——EAP 能否下发指令的主数据声明。</li>
 *   <li>{@code production_mode} 生产/工程模式（PRODUCTION/ENGINEERING，
 *       字典 {@code mds.equip_production_mode}）——工程机台收试做批，派工侧排除。</li>
 *   <li>资产四件套 {@code vendor/model_no/serial_no/asset_no} + {@code install_date}
 *       （ISA-95 PhysicalAsset 语义：角色与资产分离，搬迁换机头追溯不断）。</li>
 * </ul>
 *
 * <p>位置引用用 {@code location_type + location_id}（挂主键）：校验规则见
 * {@link EquipmentAdminService}：MATERIAL 绑 FAB/AREA/BAY，其余 kind 绑 STAGE。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_equipment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_equipment_code", columnNames = {"equipment_code", "deleted"})
})
public class CimEquipment extends EditLockDefData {

    /** 机台编码（全局唯一，例：EQP-OHT-001）。建后不可改。 */
    @Column(name = "equipment_code", length = 64, nullable = false)
    private String equipmentCode;

    /** 机台名称（例：OHT 001 号车）。 */
    @Column(name = "equipment_name", length = 128, nullable = false)
    private String equipmentName;

    /** 机型编码（逻辑引用 cim_equipment_class.class_code，页面归属的依据）。 */
    @Column(name = "class_code", length = 64, nullable = false)
    private String classCode;

    /** 位置层类型：FAB / AREA / BAY / STAGE。 */
    @Column(name = "location_type", length = 16, nullable = false)
    private String locationType;

    /** 位置记录主键（cim_fab/cim_area/cim_bay/cim_stage.id，按 location_type 定表）。 */
    @Column(name = "location_id", length = 64, nullable = false)
    private String locationId;

    /** 设备责任人（IAM username 逻辑引用；目录接入前允许手填）。 */
    @Column(name = "owner_user", length = 64)
    private String ownerUser;

    /** 通信控制模式（mds.equip_control_mode 字典，SEMI E30 口径；可空=未声明）。 */
    @Column(name = "control_mode", length = 16)
    private String controlMode;

    /** 生产/工程模式（mds.equip_production_mode 字典；默认 PRODUCTION）。 */
    @Column(name = "production_mode", length = 16, nullable = false)
    private String productionMode = "PRODUCTION";

    /** 设备厂商（PhysicalAsset）。 */
    @Column(name = "vendor", length = 128)
    private String vendor;

    /** 设备型号（PhysicalAsset）。 */
    @Column(name = "model_no", length = 128)
    private String modelNo;

    /** 设备序列号（PhysicalAsset）。 */
    @Column(name = "serial_no", length = 128)
    private String serialNo;

    /** 资产编号（PhysicalAsset，财务口径）。 */
    @Column(name = "asset_no", length = 64)
    private String assetNo;

    /** 安装日期（PhysicalAsset）。 */
    @Column(name = "install_date")
    private LocalDate installDate;

    /** 支持片径（CSV，mm：150/200/300；空=未声明）。 */
    @Column(name = "wafer_sizes", length = 64)
    private String waferSizes;

    /** 接受载具类型（CSV：CASSETTE/FOUP/FOSB/MAGAZINE；空=未声明）。 */
    @Column(name = "carrier_types", length = 128)
    private String carrierTypes;

    /** 最小批。 */
    @Column(name = "batch_min")
    private Integer batchMin;

    /** 最大批。 */
    @Column(name = "batch_max")
    private Integer batchMax;

    /** 标准产能（片/小时）。 */
    @Column(name = "standard_wph", precision = 10, scale = 2)
    private BigDecimal standardWph;

    /** 当前状态快照（mds.equip_state 字典，E10 六态口径；流转归 EAP 运行时，此处只存快照）。 */
    @Column(name = "state_code", length = 32)
    private String stateCode;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
