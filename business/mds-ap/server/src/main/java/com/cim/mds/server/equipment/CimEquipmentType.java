package com.cim.mds.server.equipment;

import com.cim.mds.server.site.EditLockDefData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 设备类型（EquipmentType）——设备域的类型主数据。
 *
 * <p>行业口径（ISA-95 Type—Instance—Capability；Kind→Type→Equipment 三层模板）：
 * kind_code 是设备大类（8 类，页面归属依据——MATERIAL→搬运设备页，其余→设备页）；
 * equip_class_code 是大类下的细类（如 ETCH/DEP/PROBER），从属关系由
 * EquipmentTypeAdminService.KIND_CLASSES 守门。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cim_equipment_type", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cim_equipment_type_code", columnNames = {"type_code", "deleted"})
})
public class CimEquipmentType extends EditLockDefData {

    /** 类型编码（全局唯一，例：ET-OHT）。 */
    @Column(name = "type_code", length = 64, nullable = false)
    private String typeCode;

    /** 类型名称（例：OHT 天车）。 */
    @Column(name = "type_name", length = 128, nullable = false)
    private String typeName;

    /** 设备大类：PROCESS/METRO/TEST/ASSY/MATERIAL/FACILITY/TOOL/OTHER（页面归属依据）。 */
    @Column(name = "kind_code", length = 16, nullable = false)
    private String kindCode;

    /** 设备细类（mds.equip_class 字典，须从属于 kindCode，可选）。 */
    @Column(name = "equip_class_code", length = 16)
    private String equipClassCode;

    /** 备注。 */
    @Column(name = "remark", length = 512)
    private String remark;

    /** 是否启用。 */
    @Column(name = "is_enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;
}
